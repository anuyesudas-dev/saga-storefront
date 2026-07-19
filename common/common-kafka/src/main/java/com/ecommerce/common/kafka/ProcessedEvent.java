package com.ecommerce.common.kafka;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Idempotency ledger entry (ADR-0002). The document id IS the eventId, so the
 * primary key enforces uniqueness. The TTL index caps growth — after expiry the
 * second idempotency layer (natural keys, state checks) still protects against
 * ancient replays.
 */
@Document(collection = "processed_events")
public class ProcessedEvent {

    @Id
    private String eventId;
    private String eventType;
    private String listener;
    @Indexed(expireAfter = "7d")
    private Instant processedAt;

    @SuppressWarnings("unused") // Spring Data
    private ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String eventType, String listener, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.listener = listener;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getListener() {
        return listener;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
