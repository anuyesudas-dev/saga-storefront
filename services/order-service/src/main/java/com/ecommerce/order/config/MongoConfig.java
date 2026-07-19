package com.ecommerce.order.config;

import com.mongodb.client.MongoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import java.time.Clock;

@Configuration
public class MongoConfig {

    /**
     * Enables @Transactional over Mongo. Requires MongoDB running as a replica set
     * (our Docker Compose and Testcontainers setups both do) — multi-document
     * transactions are what make the outbox write atomic with the order write.
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    /** Injected everywhere time is read — tests pin it to a fixed instant. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
