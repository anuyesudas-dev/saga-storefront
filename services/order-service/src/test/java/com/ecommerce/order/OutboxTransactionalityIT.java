package com.ecommerce.order;

import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.order.adapter.out.persistence.MongoEventOutbox;
import com.ecommerce.order.adapter.out.persistence.OrderMongoRepository;
import com.ecommerce.order.adapter.out.persistence.OutboxMessage;
import com.ecommerce.order.adapter.out.persistence.OutboxMessageRepository;
import com.ecommerce.order.application.command.CreateOrderCommand;
import com.ecommerce.order.application.port.EventOutbox;
import com.ecommerce.order.application.service.CreateOrderUseCase;
import com.ecommerce.order.domain.model.Order;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ADR-0003 proof against a REAL replica-set Mongo: the order write and the
 * outbox write are one transaction — a crash between them cannot leave an order
 * without its OrderCreated event, or an event without its order.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.outbox.relay.enabled=false"})
@Testcontainers
class OutboxTransactionalityIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @TestConfiguration
    static class TestBeans {

        /** Simulates a crash INSIDE the transaction, after the outbox write. */
        @Bean
        @Primary
        EventOutbox crashableOutbox(MongoEventOutbox delegate) {
            return (topic, key, envelope) -> {
                delegate.enqueue(topic, key, envelope);
                if (envelope.payload() instanceof OrderCreatedPayload p
                        && "FAIL-TX".equals(p.customerId())) {
                    throw new IllegalStateException("simulated crash after outbox write");
                }
            };
        }

        /** Keeps KafkaAdmin from contacting a broker during Mongo-focused tests. */
        @Bean
        KafkaAdmin kafkaAdmin() {
            KafkaAdmin admin = new KafkaAdmin(
                    Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1"));
            admin.setAutoCreate(false);
            return admin;
        }
    }

    @Autowired
    private CreateOrderUseCase createOrder;
    @Autowired
    private OrderMongoRepository orders;
    @Autowired
    private OutboxMessageRepository outbox;

    @BeforeEach
    void clean() {
        orders.deleteAll();
        outbox.deleteAll();
    }

    private CreateOrderCommand command(String customerId) {
        return new CreateOrderCommand(customerId, "USD", List.of(
                new CreateOrderCommand.NewOrderItem("p1", "Keyboard", 2, new BigDecimal("89.99"))));
    }

    @Test
    void orderAndOutboxEntryAreWrittenTogether() {
        Order order = createOrder.handle(command("cust-1"));

        assertThat(orders.existsById(order.getId())).isTrue();
        List<OutboxMessage> pending = outbox.findAll();
        assertThat(pending).hasSize(1);
        OutboxMessage message = pending.get(0);
        assertThat(message.getTopic()).isEqualTo(Topics.ORDER_CREATED);
        assertThat(message.getAggregateId()).isEqualTo(order.getId());
        assertThat(message.getStatus()).isEqualTo(OutboxMessage.Status.PENDING);

        // the stored payload is a full, valid envelope — what the relay will publish
        OrderCreatedPayload payload = EventJson
                .fromJson(message.getPayload(), OrderCreatedPayload.class).payload();
        assertThat(payload.orderId()).isEqualTo(order.getId());
        assertThat(payload.totalAmount()).isEqualByComparingTo("179.98");
    }

    @Test
    void crashAfterOutboxWriteRollsBackBothWrites() {
        assertThatThrownBy(() -> createOrder.handle(command("FAIL-TX")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.count()).isZero();   // no order without its event...
        assertThat(outbox.count()).isZero();   // ...and no event without its order
    }
}
