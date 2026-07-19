package com.ecommerce.notification.adapter.out.messaging;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.kafka.KafkaEnvelopeSender;
import com.ecommerce.notification.application.port.EventPublisher;
import org.springframework.stereotype.Component;

/** Kafka adapter for the application's EventPublisher port. */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaEnvelopeSender sender;

    public KafkaEventPublisher(KafkaEnvelopeSender sender) {
        this.sender = sender;
    }

    @Override
    public void publish(String topic, String key, EventEnvelope<?> envelope) {
        sender.send(topic, key, envelope);
    }
}
