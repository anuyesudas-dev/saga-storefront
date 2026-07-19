package com.ecommerce.order.adapter.out.messaging;

import com.ecommerce.order.adapter.out.persistence.OutboxMessage;
import com.ecommerce.order.adapter.out.persistence.OutboxMessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private OutboxMessageRepository outbox;
    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Captor
    private ArgumentCaptor<ProducerRecord<Object, Object>> recordCaptor;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outbox, kafkaTemplate, Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    private OutboxMessage pending(String eventId, String orderId) {
        return OutboxMessage.pending(eventId, orderId, "order-created", "OrderCreated",
                "{\"eventId\":\"" + eventId + "\"}", NOW);
    }

    @Test
    void publishesPendingEntriesOldestFirstAndMarksThemPublished() {
        OutboxMessage first = pending("e1", "order-1");
        OutboxMessage second = pending("e2", "order-2");
        when(outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxMessage.Status.PENDING))
                .thenReturn(List.of(first, second));
        when(kafkaTemplate.send(recordCaptor.capture())).thenReturn(
                CompletableFuture.completedFuture(new SendResult<>(null, null)));

        relay.publishPending();

        assertThat(recordCaptor.getAllValues()).hasSize(2);
        assertThat(recordCaptor.getAllValues().get(0).key()).isEqualTo("order-1");
        assertThat(first.getStatus()).isEqualTo(OutboxMessage.Status.PUBLISHED);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getStatus()).isEqualTo(OutboxMessage.Status.PUBLISHED);
        verify(outbox).save(first);
        verify(outbox).save(second);
    }

    @Test
    void sendFailureStopsTheBatchSoOrderingIsPreserved() {
        OutboxMessage first = pending("e1", "order-1");
        OutboxMessage second = pending("e2", "order-1"); // same aggregate — order matters
        when(outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxMessage.Status.PENDING))
                .thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        relay.publishPending();

        assertThat(first.getStatus()).isEqualTo(OutboxMessage.Status.PENDING);
        assertThat(second.getStatus()).isEqualTo(OutboxMessage.Status.PENDING);
        verify(outbox, never()).save(any());
    }
}
