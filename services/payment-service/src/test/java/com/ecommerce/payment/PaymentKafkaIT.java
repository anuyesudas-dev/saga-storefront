package com.ecommerce.payment;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.common.events.inventory.ReservedLine;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.payment.PaymentFailedPayload;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.payment.adapter.out.persistence.PaymentMongoRepository;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentStatus;
import com.ecommerce.payment.domain.port.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Payment over a REAL broker: InventoryReserved consumption drives a (simulated)
 * charge and the outcome event. success-rate is pinned to 1.0 so the only
 * failure path exercised is the deterministic .99 decline rule.
 */
@SpringBootTest(properties = {
        "payment.simulation.success-rate=1.0",
        "payment.simulation.latency-ms=0"})
@Testcontainers
class PaymentKafkaIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Autowired
    private PaymentRepository paymentPort;
    @Autowired
    private PaymentMongoRepository payments; // cleanup only
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @BeforeEach
    void clean() {
        payments.deleteAll();
    }

    private void sendInventoryReserved(String orderId, BigDecimal amount) {
        EventEnvelope<InventoryReservedPayload> event = EventEnvelope.of(
                EventTypes.INVENTORY_RESERVED, "corr-it", "inventory-service",
                new InventoryReservedPayload(orderId, "cust-it", "resv-1",
                        List.of(new ReservedLine("p1", 2)), amount, "USD"));
        kafkaTemplate.send(Topics.INVENTORY_RESERVED, orderId, EventJson.toJson(event));
    }

    @Test
    void inventoryReservedTriggersChargeAndPublishesPaymentSuccess() {
        String orderId = "it-order-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = consumerFor(Topics.PAYMENT_SUCCESS)) {
            sendInventoryReserved(orderId, new BigDecimal("179.98"));

            // assert on the Optional: a non-assertion throw (empty Optional while
            // the consumer group is still rebalancing) would end the await early
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(paymentPort.findByOrderId(orderId)).hasValueSatisfying(p ->
                            assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED)));
            Payment payment = paymentPort.findByOrderId(orderId).orElseThrow();

            ConsumerRecord<String, String> record = awaitRecord(consumer,
                    Duration.ofSeconds(30), r -> orderId.equals(r.key()));
            EventEnvelope<PaymentCompletedPayload> envelope =
                    EventJson.fromJson(record.value(), PaymentCompletedPayload.class);
            assertThat(envelope.eventType()).isEqualTo(EventTypes.PAYMENT_COMPLETED);
            assertThat(envelope.payload().paymentId()).isEqualTo(payment.getId());
            assertThat(envelope.payload().amount()).isEqualByComparingTo("179.98");
        }
    }

    @Test
    void deterministicDeclinePublishesPaymentFailedWithReason() {
        String orderId = "it-order-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = consumerFor(Topics.PAYMENT_FAILED)) {
            sendInventoryReserved(orderId, new BigDecimal("19.99")); // .99 always declines

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(paymentPort.findByOrderId(orderId)).hasValueSatisfying(p ->
                            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED)));

            ConsumerRecord<String, String> record = awaitRecord(consumer,
                    Duration.ofSeconds(30), r -> orderId.equals(r.key()));
            EventEnvelope<PaymentFailedPayload> envelope =
                    EventJson.fromJson(record.value(), PaymentFailedPayload.class);
            assertThat(envelope.eventType()).isEqualTo(EventTypes.PAYMENT_FAILED);
            assertThat(envelope.payload().reason()).isEqualTo("INSUFFICIENT_FUNDS");
        }
    }

    private KafkaConsumer<String, String> consumerFor(String topic) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer, Duration timeout,
            Predicate<ConsumerRecord<String, String>> matches) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (matches.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("no matching record within " + timeout);
    }
}
