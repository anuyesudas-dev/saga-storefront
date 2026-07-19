package com.ecommerce.common.kafka;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.serialization.EventJson;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentEventProcessorTest {

    record TestPayload(String orderId) {
    }

    @Mock
    private ProcessedEventStore store;
    private SimpleMeterRegistry meterRegistry;
    private IdempotentEventProcessor processor;

    private final EventEnvelope<TestPayload> envelope =
            EventEnvelope.of("TestEvent", "corr-42", "test-service", new TestPayload("order-1"));
    private final String json = EventJson.toJson(envelope);

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        processor = new IdempotentEventProcessor(store, meterRegistry, "test-service");
    }

    @Test
    void handlesFreshEventAndRecordsItAfterSuccess() {
        when(store.alreadyProcessed(envelope.eventId())).thenReturn(false);
        AtomicReference<String> mdcDuringHandling = new AtomicReference<>();
        AtomicReference<TestPayload> received = new AtomicReference<>();

        processor.process(json, TestPayload.class, env -> {
            received.set(env.payload());
            mdcDuringHandling.set(MDC.get("correlationId"));
        });

        assertThat(received.get().orderId()).isEqualTo("order-1");
        assertThat(mdcDuringHandling.get()).isEqualTo("corr-42"); // correlation restored from envelope
        assertThat(MDC.get("correlationId")).isNull();            // and cleared afterwards
        verify(store).record(envelope.eventId(), "TestEvent", "test-service");
    }

    @Test
    void skipsDuplicateWithoutInvokingHandler() {
        when(store.alreadyProcessed(envelope.eventId())).thenReturn(true);
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        processor.process(json, TestPayload.class, env -> called.set(true));

        assertThat(called.get()).isFalse();
        verify(store, never()).record(any(), any(), any());
        assertThat(meterRegistry.counter("kafka.consumer.duplicates", "event", "TestEvent").count())
                .isEqualTo(1.0);
    }

    @Test
    void handlerFailureDoesNotRecordSoRedeliveryReprocesses() {
        when(store.alreadyProcessed(envelope.eventId())).thenReturn(false);

        assertThatThrownBy(() -> processor.process(json, TestPayload.class, env -> {
            throw new IllegalStateException("transient db error");
        })).isInstanceOf(IllegalStateException.class);

        verify(store, never()).record(any(), any(), any());
        assertThat(MDC.get("correlationId")).isNull(); // cleared even on failure
    }

    @Test
    void malformedMessageThrowsPoisonPillException() {
        assertThatThrownBy(() -> processor.process("{not json", TestPayload.class, env -> {
        })).isInstanceOf(java.io.UncheckedIOException.class);
    }
}
