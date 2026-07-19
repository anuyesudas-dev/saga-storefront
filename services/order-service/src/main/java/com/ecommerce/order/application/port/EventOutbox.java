package com.ecommerce.order.application.port;

import com.ecommerce.common.events.EventEnvelope;

/**
 * Outbound port for event publication via the transactional outbox (ADR-0003).
 * Enqueueing happens in the same Mongo transaction as the aggregate write; a
 * relay publishes to Kafka asynchronously.
 */
public interface EventOutbox {

    /**
     * @param topic destination Kafka topic
     * @param key   Kafka partition key (always the orderId — preserves per-order ordering)
     */
    void enqueue(String topic, String key, EventEnvelope<?> envelope);
}
