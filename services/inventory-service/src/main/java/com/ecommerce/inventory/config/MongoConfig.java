package com.ecommerce.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class MongoConfig {

    /** Multi-item reservations must be atomic across product documents — requires replica set. */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    /** Programmatic transactions for reserve-then-publish (publish stays outside the tx). */
    @Bean
    public TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
