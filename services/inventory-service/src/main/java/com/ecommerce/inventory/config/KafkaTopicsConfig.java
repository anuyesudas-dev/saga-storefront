package com.ecommerce.inventory.config;

import com.ecommerce.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Produces: inventory topics. Consumes: order + payment topics — owns their DLTs. */
@Configuration
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;

    @Bean
    public NewTopic inventoryReservedTopic() {
        return topic(Topics.INVENTORY_RESERVED);
    }

    @Bean
    public NewTopic inventoryFailedTopic() {
        return topic(Topics.INVENTORY_FAILED);
    }

    @Bean
    public NewTopic orderCreatedDlt() {
        return topic(Topics.ORDER_CREATED + Topics.DLT_SUFFIX);
    }

    @Bean
    public NewTopic orderCancelledDlt() {
        return topic(Topics.ORDER_CANCELLED + Topics.DLT_SUFFIX);
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
