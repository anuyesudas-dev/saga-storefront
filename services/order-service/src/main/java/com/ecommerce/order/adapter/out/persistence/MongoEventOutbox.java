package com.ecommerce.order.adapter.out.persistence;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.order.application.port.EventOutbox;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Persists events to the outbox collection. Runs inside the caller's Mongo
 * transaction — that's the whole point (ADR-0003).
 */
@Component
public class MongoEventOutbox implements EventOutbox {

    private final OutboxMessageRepository repository;
    private final Clock clock;

    public MongoEventOutbox(OutboxMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void enqueue(String topic, String key, EventEnvelope<?> envelope) {
        repository.save(OutboxMessage.pending(
                envelope.eventId(), key, topic, envelope.eventType(),
                EventJson.toJson(envelope), clock.instant()));
    }
}
