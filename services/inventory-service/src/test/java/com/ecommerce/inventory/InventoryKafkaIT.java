package com.ecommerce.inventory;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.order.OrderLine;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.inventory.adapter.out.persistence.ProductMongoRepository;
import com.ecommerce.inventory.adapter.out.persistence.ReservationMongoRepository;
import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.port.ProductRepository;
import com.ecommerce.inventory.domain.port.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Inventory over a REAL broker: OrderCreated consumption drives reservation and
 * the enriched InventoryReserved event; a poison pill (malformed JSON) is
 * dead-lettered immediately — no retries — per ADR-0006.
 */
@SpringBootTest
@Testcontainers
class InventoryKafkaIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Autowired
    private ProductRepository productPort;
    @Autowired
    private ReservationRepository reservationPort;
    @Autowired
    private ProductMongoRepository products; // cleanup only
    @Autowired
    private ReservationMongoRepository reservations; // cleanup only
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void clean() {
        products.deleteAll();
        reservations.deleteAll();
    }

    @Test
    void orderCreatedReservesStockAndPublishesEnrichedInventoryReserved() {
        productPort.save(Product.create("p1", "Keyboard", 5, Instant.now()));
        String orderId = "it-order-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = consumerFor(Topics.INVENTORY_RESERVED)) {
            EventEnvelope<OrderCreatedPayload> event = EventEnvelope.of(
                    EventTypes.ORDER_CREATED, "corr-it", "order-service",
                    new OrderCreatedPayload(orderId, "cust-it",
                            List.of(new OrderLine("p1", "Keyboard", 2, new BigDecimal("89.99"))),
                            new BigDecimal("179.98"), "USD"));
            kafkaTemplate.send(Topics.ORDER_CREATED, orderId, EventJson.toJson(event));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                Product p = productPort.findById("p1").orElseThrow();
                assertThat(p.getAvailableQty()).isEqualTo(3);
                assertThat(p.getReservedQty()).isEqualTo(2);
            });
            assertThat(reservationPort.findByOrderId(orderId).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.ACTIVE);

            ConsumerRecord<String, String> record = awaitRecord(consumer,
                    Duration.ofSeconds(30), r -> orderId.equals(r.key()));
            InventoryReservedPayload payload =
                    EventJson.fromJson(record.value(), InventoryReservedPayload.class).payload();
            // enrichment survived the real broker round-trip (ADR-0004)
            assertThat(payload.totalAmount()).isEqualByComparingTo("179.98");
            assertThat(payload.currency()).isEqualTo("USD");
        }
    }

    @Test
    void poisonPillIsDeadLetteredImmediatelyWithoutRetries() {
        String poison = "{this is not an event envelope";

        try (KafkaConsumer<String, String> consumer =
                     consumerFor(Topics.ORDER_CREATED + Topics.DLT_SUFFIX)) {
            kafkaTemplate.send(Topics.ORDER_CREATED, "poison-key", poison);

            ConsumerRecord<String, String> dead = awaitRecord(consumer,
                    Duration.ofSeconds(60), r -> "poison-key".equals(r.key()));

            assertThat(dead.value()).isEqualTo(poison); // original payload preserved
            assertThat(dead.headers().lastHeader("kafka_dlt-original-topic").value())
                    .isEqualTo(Topics.ORDER_CREATED.getBytes(StandardCharsets.UTF_8));
            // fqcn holds the container wrapper (ListenerExecutionFailedException);
            // the root cause rides in the -cause-fqcn header
            assertThat(new String(
                    dead.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value(),
                    StandardCharsets.UTF_8)).contains("UncheckedIOException");

            assertThat(meterRegistry.counter("kafka.dlq.messages",
                    "topic", Topics.ORDER_CREATED).count()).isGreaterThanOrEqualTo(1.0);
            // not-retryable classification: the poison pill burned zero retries
            assertThat(meterRegistry.counter("kafka.consumer.retries",
                    "topic", Topics.ORDER_CREATED).count()).isZero();
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
