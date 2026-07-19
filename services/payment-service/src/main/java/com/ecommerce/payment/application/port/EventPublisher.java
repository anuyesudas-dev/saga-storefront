package com.ecommerce.payment.application.port;

import com.ecommerce.common.events.EventEnvelope;

/** Outbound port for publishing events to Kafka (direct publish — see ADR-0003). */
public interface EventPublisher {

    void publish(String topic, String key, EventEnvelope<?> envelope);
}
