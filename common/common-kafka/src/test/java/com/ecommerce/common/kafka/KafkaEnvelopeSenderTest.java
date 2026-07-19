package com.ecommerce.common.kafka;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.KafkaHeaders;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEnvelopeSenderTest {

    record TestPayload(String orderId) {
    }

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Captor
    private ArgumentCaptor<ProducerRecord<Object, Object>> recordCaptor;

    @Test
    void sendsJsonWithCorrelationAndTypeHeaders() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(kafkaTemplate.send(recordCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        new SendResult<>(null, null)));
        EventEnvelope<TestPayload> envelope = EventEnvelope.of(
                "TestEvent", "corr-42", "test-service", new TestPayload("order-1"));

        new KafkaEnvelopeSender(kafkaTemplate, meterRegistry)
                .send("some-topic", "order-1", envelope);

        ProducerRecord<Object, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("some-topic");
        assertThat(record.key()).isEqualTo("order-1");
        assertThat((String) record.value())
                .contains("\"eventType\":\"TestEvent\"")
                .contains("\"orderId\":\"order-1\"");
        assertThat(record.headers().lastHeader(KafkaHeaders.CORRELATION_ID).value())
                .isEqualTo("corr-42".getBytes(StandardCharsets.UTF_8));
        assertThat(record.headers().lastHeader(KafkaHeaders.EVENT_TYPE).value())
                .isEqualTo("TestEvent".getBytes(StandardCharsets.UTF_8));
        assertThat(meterRegistry.counter("events.published", "topic", "some-topic").count())
                .isEqualTo(1.0);
    }
}
