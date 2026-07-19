package com.ecommerce.order.adapter.out.messaging;

import com.ecommerce.common.events.KafkaHeaders;
import com.ecommerce.order.adapter.out.persistence.OutboxMessage;
import com.ecommerce.order.adapter.out.persistence.OutboxMessageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes PENDING outbox entries to Kafka, oldest first (ADR-0003).
 *
 * <p>A send failure stops the whole batch — continuing past a failed entry
 * could publish a later event for the same order before an earlier one,
 * breaking per-order ordering. The next poll retries from the failed entry;
 * re-publishing an already-sent event is safe because the eventId (= outbox id)
 * is deduplicated by every consumer.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true",
        matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMessageRepository outbox;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(OutboxMessageRepository outbox, KafkaTemplate<Object, Object> kafkaTemplate,
                       Clock clock, MeterRegistry meterRegistry) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("outbox.pending", outbox,
                repo -> repo.countByStatus(OutboxMessage.Status.PENDING));
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.poll-interval-ms:500}")
    public void publishPending() {
        List<OutboxMessage> batch =
                outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxMessage.Status.PENDING);
        for (OutboxMessage message : batch) {
            ProducerRecord<Object, Object> record = new ProducerRecord<>(
                    message.getTopic(), message.getAggregateId(), message.getPayload());
            record.headers().add(new RecordHeader(KafkaHeaders.EVENT_TYPE,
                    message.getEventType().getBytes(StandardCharsets.UTF_8)));
            try {
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                meterRegistry.counter("outbox.publish.failures").increment();
                log.error("Outbox publish failed for {} (eventId={}) — stopping batch, will retry",
                        message.getTopic(), message.getId(), e);
                return;
            }
            message.markPublished(clock.instant());
            outbox.save(message);
            meterRegistry.counter("outbox.published", "topic", message.getTopic()).increment();
            log.info("Outbox relayed {} to {} (eventId={})",
                    message.getEventType(), message.getTopic(), message.getId());
        }
    }
}
