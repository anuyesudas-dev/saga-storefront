package com.ecommerce.order.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Transactional outbox entry (ADR-0003). The document id IS the eventId, so a
 * relay retry republishes with the same eventId and downstream idempotency
 * (ADR-0002) dedupes it.
 */
@Document(collection = "outbox")
public class OutboxMessage {

    public enum Status {PENDING, PUBLISHED}

    @Id
    private String id;
    /** Kafka partition key (the orderId). */
    private String aggregateId;
    private String topic;
    private String eventType;
    /** The full serialized EventEnvelope JSON. */
    private String payload;
    @Indexed
    private Status status;
    private Instant createdAt;
    private Instant publishedAt;

    @SuppressWarnings("unused") // Spring Data
    private OutboxMessage() {
    }

    public static OutboxMessage pending(String eventId, String aggregateId, String topic,
                                        String eventType, String payload, Instant now) {
        OutboxMessage m = new OutboxMessage();
        m.id = eventId;
        m.aggregateId = aggregateId;
        m.topic = topic;
        m.eventType = eventType;
        m.payload = payload;
        m.status = Status.PENDING;
        m.createdAt = now;
        return m;
    }

    public void markPublished(Instant now) {
        this.status = Status.PUBLISHED;
        this.publishedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
