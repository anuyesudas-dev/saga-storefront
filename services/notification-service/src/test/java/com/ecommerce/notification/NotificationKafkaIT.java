package com.ecommerce.notification;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.notification.NotificationSentPayload;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.notification.adapter.out.persistence.NotificationMongoRepository;
import com.ecommerce.notification.domain.model.NotificationType;
import com.ecommerce.notification.domain.port.NotificationRepository;
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
 * Notification over a REAL broker: a saga event in, a persisted notification
 * plus a NotificationSent event out — the full consume→dispatch→publish path.
 */
@SpringBootTest
@Testcontainers
class NotificationKafkaIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Autowired
    private NotificationRepository notificationPort;
    @Autowired
    private NotificationMongoRepository notifications; // cleanup only
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @BeforeEach
    void clean() {
        notifications.deleteAll();
    }

    @Test
    void paymentCompletedProducesReceiptAndPublishesNotificationSent() {
        String orderId = "it-order-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = consumerFor(Topics.NOTIFICATION_SENT)) {
            EventEnvelope<PaymentCompletedPayload> event = EventEnvelope.of(
                    EventTypes.PAYMENT_COMPLETED, "corr-it", "payment-service",
                    new PaymentCompletedPayload(orderId, "cust-it", "pay-1",
                            new BigDecimal("179.98"), "USD"));
            kafkaTemplate.send(Topics.PAYMENT_SUCCESS, orderId, EventJson.toJson(event));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(notificationPort.findByOrderId(orderId))
                            .anyMatch(n -> n.getType() == NotificationType.PAYMENT_RECEIPT));

            ConsumerRecord<String, String> record = awaitRecord(consumer,
                    Duration.ofSeconds(30), r -> orderId.equals(r.key()));
            EventEnvelope<NotificationSentPayload> envelope =
                    EventJson.fromJson(record.value(), NotificationSentPayload.class);
            assertThat(envelope.eventType()).isEqualTo(EventTypes.NOTIFICATION_SENT);
            assertThat(envelope.payload().notificationType())
                    .isEqualTo(NotificationType.PAYMENT_RECEIPT.name());
            assertThat(envelope.payload().status()).isEqualTo("SENT");
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
