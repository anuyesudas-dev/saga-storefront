package com.ecommerce.order.config;

import com.ecommerce.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declarative topic creation (broker auto-create stays off). A service creates
 * the topics it PRODUCES, plus the DLTs for topics it CONSUMES. 3 partitions:
 * per-order ordering via keying, parallelism headroom. Replication factor 1 is
 * a single-broker local setting — production runs RF 3, min.insync.replicas 2.
 */
@Configuration
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;

    @Bean
    public NewTopic orderCreatedTopic() {
        return topic(Topics.ORDER_CREATED);
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return topic(Topics.ORDER_CANCELLED);
    }

    // DLTs need the same partition count as their source: the dead-letter
    // recoverer targets the same partition number the record failed on.
    @Bean
    public NewTopic inventoryReservedDlt() {
        return topic(Topics.INVENTORY_RESERVED + Topics.DLT_SUFFIX);
    }

    @Bean
    public NewTopic inventoryFailedDlt() {
        return topic(Topics.INVENTORY_FAILED + Topics.DLT_SUFFIX);
    }

    @Bean
    public NewTopic paymentSuccessDlt() {
        return topic(Topics.PAYMENT_SUCCESS + Topics.DLT_SUFFIX);
    }

    @Bean
    public NewTopic paymentFailedDlt() {
        return topic(Topics.PAYMENT_FAILED + Topics.DLT_SUFFIX);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
