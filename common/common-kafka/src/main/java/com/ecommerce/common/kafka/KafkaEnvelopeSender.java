package com.ecommerce.common.kafka;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.KafkaHeaders;
import com.ecommerce.common.events.serialization.EventJson;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Serializes envelopes to the wire format and sends them. Correlation id and
 * event type are duplicated into Kafka headers so CLI tooling can filter
 * without parsing bodies.
 */
public class KafkaEnvelopeSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaEnvelopeSender.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public KafkaEnvelopeSender(KafkaTemplate<Object, Object> kafkaTemplate,
                               MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    public void send(String topic, String key, EventEnvelope<?> envelope) {
        ProducerRecord<Object, Object> record =
                new ProducerRecord<>(topic, key, EventJson.toJson(envelope));
        record.headers()
                .add(new RecordHeader(KafkaHeaders.CORRELATION_ID,
                        envelope.correlationId().getBytes(StandardCharsets.UTF_8)))
                .add(new RecordHeader(KafkaHeaders.EVENT_TYPE,
                        envelope.eventType().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                meterRegistry.counter("events.published", "topic", topic).increment();
                log.info("Published {} to {} (key={}, eventId={})",
                        envelope.eventType(), topic, key, envelope.eventId());
            } else {
                meterRegistry.counter("events.publish.failures", "topic", topic).increment();
                log.error("Failed to publish {} to {} (key={}, eventId={})",
                        envelope.eventType(), topic, key, envelope.eventId(), ex);
            }
        });
    }
}
