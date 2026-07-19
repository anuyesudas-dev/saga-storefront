package com.ecommerce.common.kafka;

import com.ecommerce.common.events.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.io.UncheckedIOException;
import java.time.Clock;

/**
 * Internal starter: consistent messaging behavior for every service that adds
 * the dependency — envelope publishing, idempotent consumption, blocking
 * exponential retries (preserves per-key ordering, ADR-0006), dead-lettering
 * to {@code <topic>.DLT}, and the metrics behind the Grafana panels.
 */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, MongoDataAutoConfiguration.class})
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(CommonKafkaProperties.class)
public class CommonKafkaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CommonKafkaAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    public KafkaEnvelopeSender kafkaEnvelopeSender(KafkaTemplate<Object, Object> kafkaTemplate,
                                                   MeterRegistry meterRegistry) {
        return new KafkaEnvelopeSender(kafkaTemplate, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MongoTemplate.class)
    public ProcessedEventStore processedEventStore(MongoTemplate mongoTemplate, Clock clock) {
        return new ProcessedEventStore(mongoTemplate, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ProcessedEventStore.class)
    public IdempotentEventProcessor idempotentEventProcessor(
            ProcessedEventStore store, MeterRegistry meterRegistry,
            @Value("${spring.application.name}") String serviceName) {
        return new IdempotentEventProcessor(store, meterRegistry, serviceName);
    }

    /**
     * Picked up automatically by Boot's listener container factory. Failed
     * records retry with exponential backoff, then dead-letter to
     * {@code <topic>.DLT} (same partition) with error details in headers.
     * Malformed envelopes ({@link UncheckedIOException}) skip retries entirely —
     * a poison pill never becomes deliverable by retrying.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 CommonKafkaProperties properties,
                                                 MeterRegistry meterRegistry) {
        // Spring Kafka's default suffix is "-dlt"; the platform's DLT topics
        // (declared per consumer in each service's KafkaTopicsConfig) use ".DLT".
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(
                        record.topic() + Topics.DLT_SUFFIX, record.partition()));
        ConsumerRecordRecoverer countingRecoverer = (record, ex) -> {
            meterRegistry.counter("kafka.dlq.messages", "topic", record.topic()).increment();
            log.error("Dead-lettering record from {} (partition={}, offset={}): {}",
                    record.topic(), record.partition(), record.offset(), ex.getMessage());
            dlt.accept(record, ex);
        };

        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(properties.maxRetries());
        backOff.setInitialInterval(properties.initialIntervalMs());
        backOff.setMultiplier(properties.multiplier());
        backOff.setMaxInterval(properties.maxIntervalMs());

        DefaultErrorHandler handler = new DefaultErrorHandler(countingRecoverer, backOff);
        handler.addNotRetryableExceptions(UncheckedIOException.class);
        handler.setRetryListeners((record, ex, attempt) -> {
            // failedDelivery fires with attempt=1 on the FIRST failure, before
            // any retry has happened — only attempts beyond it are retries.
            if (attempt > 1) {
                meterRegistry.counter("kafka.consumer.retries", "topic", record.topic()).increment();
                log.warn("Retry {} for record from {} (offset={}): {}",
                        attempt - 1, record.topic(), record.offset(), ex.getMessage());
            }
        });
        return handler;
    }

    /** Fallback clock for services that don't define their own. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
