package com.ecommerce.notification.application.port;

import com.ecommerce.common.events.EventEnvelope;

/** Outbound port for publishing to the notification-sent audit stream. */
public interface EventPublisher {

    void publish(String topic, String key, EventEnvelope<?> envelope);
}
