package com.ecommerce.inventory.application.port;

import com.ecommerce.common.events.EventEnvelope;

/**
 * Outbound port for publishing events to Kafka. Inventory publishes directly
 * after its DB commit (no outbox — ADR-0003 reserves that for the saga entry
 * point); idempotent consumers downstream absorb the rare duplicate.
 */
public interface EventPublisher {

    void publish(String topic, String key, EventEnvelope<?> envelope);
}
