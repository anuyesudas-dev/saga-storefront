package com.ecommerce.common.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Standard envelope wrapping every event on every topic.
 *
 * <p>{@code eventId} is the idempotency key: consumers record it in their
 * {@code processed_events} ledger and skip events they have already handled.
 * A logical event keeps the same {@code eventId} across publish retries.
 *
 * <p>{@code correlationId} is minted once per saga (at order creation) and
 * copied verbatim into every downstream event, so logs across all services
 * join on it.
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String source,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
    }

    /** Creates a v1 envelope with a fresh eventId and the current timestamp. */
    public static <T> EventEnvelope<T> of(String eventType, String correlationId, String source, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(), eventType, 1, Instant.now(), correlationId, source, payload);
    }
}
