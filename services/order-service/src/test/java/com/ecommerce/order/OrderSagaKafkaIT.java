package com.ecommerce.order;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.common.events.inventory.ReservedLine;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.order.adapter.out.persistence.OrderMongoRepository;
import com.ecommerce.order.adapter.out.persistence.OutboxMessage;
import com.ecommerce.order.adapter.out.persistence.OutboxMessageRepository;
import com.ecommerce.order.application.command.CreateOrderCommand;
import com.ecommerce.order.application.service.CreateOrderUseCase;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.OrderRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The saga over a REAL broker: outbox relay -> Kafka -> downstream, and
 * downstream events -> listeners -> state machine, with the processed-events
 * ledger deduplicating redeliveries.
 */
@SpringBootTest(properties = "app.outbox.relay.poll-interval-ms=200")
@Testcontainers
class OrderSagaKafkaIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @Autowired
    private CreateOrderUseCase createOrder;
    @Autowired
    private OrderRepository orderPort;
    @Autowired
    private OrderMongoRepository orders; // cleanup only
    @Autowired
    private OutboxMessageRepository outbox;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void clean() {
        orders.deleteAll();
        outbox.deleteAll();
    }

    private Order newOrder() {
        return createOrder.handle(new CreateOrderCommand("cust-it", "USD", List.of(
                new CreateOrderCommand.NewOrderItem("p1", "Keyboard", 2, new BigDecimal("89.99")))));
    }

    @Test
    void outboxRelayPublishesOrderCreatedToKafka() {
        try (KafkaConsumer<String, String> consumer = consumerFor(Topics.ORDER_CREATED)) {
            Order order = newOrder();

            ConsumerRecord<String, String> record = awaitRecord(consumer,
                    Duration.ofSeconds(30), r -> order.getId().equals(r.key()));

            EventEnvelope<OrderCreatedPayload> envelope =
                    EventJson.fromJson(record.value(), OrderCreatedPayload.class);
            assertThat(envelope.eventType()).isEqualTo(EventTypes.ORDER_CREATED);
            assertThat(envelope.payload().orderId()).isEqualTo(order.getId());
            assertThat(envelope.payload().totalAmount()).isEqualByComparingTo("179.98");
            assertThat(record.headers().lastHeader("x-event-type").value())
                    .isEqualTo("OrderCreated".getBytes(StandardCharsets.UTF_8));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(outbox.findAll())
                            .allMatch(m -> m.getStatus() == OutboxMessage.Status.PUBLISHED));
        }
    }

    @Test
    void downstreamEventsDriveStateMachineAndLedgerDeduplicatesRedelivery() {
        Order order = newOrder();

        // inventory reserves -> INVENTORY_RESERVED
        EventEnvelope<InventoryReservedPayload> reserved = EventEnvelope.of(
                EventTypes.INVENTORY_RESERVED, "corr-it", "inventory-service",
                new InventoryReservedPayload(order.getId(), "cust-it", "resv-1",
                        List.of(new ReservedLine("p1", 2)), new BigDecimal("179.98"), "USD"));
        String reservedJson = EventJson.toJson(reserved);
        kafkaTemplate.send(Topics.INVENTORY_RESERVED, order.getId(), reservedJson);
        awaitStatus(order.getId(), OrderStatus.INVENTORY_RESERVED);

        // exact redelivery (same eventId): ledger skips it, no anomaly recorded
        kafkaTemplate.send(Topics.INVENTORY_RESERVED, order.getId(), reservedJson);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(meterRegistry.counter("kafka.consumer.duplicates",
                        "event", EventTypes.INVENTORY_RESERVED).count()).isGreaterThanOrEqualTo(1.0));
        assertThat(meterRegistry.find("saga.anomalies").counters()).isEmpty();
        assertThat(orderPort.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.INVENTORY_RESERVED);

        // payment completes -> PAID
        EventEnvelope<PaymentCompletedPayload> paid = EventEnvelope.of(
                EventTypes.PAYMENT_COMPLETED, "corr-it", "payment-service",
                new PaymentCompletedPayload(order.getId(), "cust-it", "pay-1",
                        new BigDecimal("179.98"), "USD"));
        kafkaTemplate.send(Topics.PAYMENT_SUCCESS, order.getId(), EventJson.toJson(paid));
        awaitStatus(order.getId(), OrderStatus.PAID);
    }

    private void awaitStatus(String orderId, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orderPort.findById(orderId).orElseThrow().getStatus())
                        .isEqualTo(expected));
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
