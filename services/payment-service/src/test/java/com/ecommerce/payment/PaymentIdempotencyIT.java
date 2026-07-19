package com.ecommerce.payment;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.Topics;
import com.ecommerce.payment.application.command.ProcessPaymentCommand;
import com.ecommerce.payment.application.port.EventPublisher;
import com.ecommerce.payment.application.service.ProcessPaymentUseCase;
import com.ecommerce.payment.adapter.out.persistence.PaymentMongoRepository;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Mongo check of the payment idempotency posture: a redelivered
 * InventoryReserved never charges twice (unique orderId index), but DOES
 * republish the stored outcome so a lost publish can't strand the saga.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "payment.simulation.success-rate=1.0",
        "payment.simulation.latency-ms=0"})
@Testcontainers
class PaymentIdempotencyIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    static class RecordingPublisher implements EventPublisher {
        final List<String> topics = new CopyOnWriteArrayList<>();

        @Override
        public void publish(String topic, String key, EventEnvelope<?> envelope) {
            topics.add(topic);
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }

        @Bean
        KafkaAdmin kafkaAdmin() {
            KafkaAdmin admin = new KafkaAdmin(
                    Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1"));
            admin.setAutoCreate(false);
            return admin;
        }
    }

    @Autowired
    private ProcessPaymentUseCase processPayment;
    @Autowired
    private PaymentMongoRepository payments;
    @Autowired
    private RecordingPublisher recorder;

    @BeforeEach
    void clean() {
        payments.deleteAll();
        recorder.topics.clear();
    }

    @Test
    void redeliveryChargesOnceButRepublishesTheOutcome() {
        ProcessPaymentCommand command =
                new ProcessPaymentCommand("order-1", "cust-1", new BigDecimal("50.00"), "USD");

        processPayment.handle(command);
        processPayment.handle(command); // redelivered event

        assertThat(payments.count()).isEqualTo(1); // one charge, ever
        assertThat(payments.findByOrderId("order-1")).isPresent();
        // outcome republished on redelivery — a saga can't be stranded by a lost publish
        assertThat(recorder.topics)
                .containsExactly(Topics.PAYMENT_SUCCESS, Topics.PAYMENT_SUCCESS);
    }
}
