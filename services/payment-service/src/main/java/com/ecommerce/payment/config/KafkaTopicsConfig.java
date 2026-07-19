package com.ecommerce.payment.config;

import com.ecommerce.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Produces: payment topics. Consumes: inventory-reserved — owns its DLT. */
@Configuration
public class KafkaTopicsConfig {

    private static final int PARTITIONS = 3;

    @Bean
    public NewTopic paymentSuccessTopic() {
        return topic(Topics.PAYMENT_SUCCESS);
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return topic(Topics.PAYMENT_FAILED);
    }

    @Bean
    public NewTopic inventoryReservedDlt() {
        return topic(Topics.INVENTORY_RESERVED + Topics.DLT_SUFFIX);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }
}
