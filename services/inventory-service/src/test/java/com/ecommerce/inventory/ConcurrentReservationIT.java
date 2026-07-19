package com.ecommerce.inventory;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.Topics;
import com.ecommerce.inventory.adapter.out.persistence.ProductMongoRepository;
import com.ecommerce.inventory.adapter.out.persistence.ReservationMongoRepository;
import com.ecommerce.inventory.application.command.ReserveInventoryCommand;
import com.ecommerce.inventory.application.port.EventPublisher;
import com.ecommerce.inventory.application.service.ReserveInventoryUseCase;
import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.port.ProductRepository;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The no-oversell proof (Phase 3, decision d) against a REAL replica-set Mongo:
 * optimistic locking on Product plus transactional reservation means concurrent
 * orders can never take stock below zero.
 *
 * <p>Concurrent writers lose with transient errors (optimistic-lock version
 * conflicts / Mongo write conflicts); in production the Kafka error handler
 * redelivers — the retry loop here plays that role.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class ConcurrentReservationIT {

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
    private ReserveInventoryUseCase reserve;
    @Autowired
    private ProductMongoRepository products; // cleanup only — domain ops go through the port
    @Autowired
    private ProductRepository productPort;
    @Autowired
    private ReservationMongoRepository reservations;
    @Autowired
    private RecordingPublisher recorder;

    @BeforeEach
    void clean() {
        products.deleteAll();
        reservations.deleteAll();
        recorder.topics.clear();
    }

    private ReserveInventoryCommand orderFor(String orderId, int quantity) {
        return new ReserveInventoryCommand(orderId, "cust-1",
                List.of(new ReserveInventoryCommand.Line("p1", quantity)),
                new BigDecimal("10.00"), "USD");
    }

    /** Mimics Kafka redelivery: transient data-access conflicts get retried. */
    private void reserveWithRedelivery(ReserveInventoryCommand command) {
        for (int attempt = 0; ; attempt++) {
            try {
                reserve.handle(command);
                return;
            } catch (DataAccessException e) {
                if (attempt >= 30) {
                    throw e;
                }
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Test
    void tenConcurrentOrdersForFiveUnitsNeverOversell() throws Exception {
        productPort.save(Product.create("p1", "Keyboard", new java.math.BigDecimal("19.99"), 5, Instant.now()));

        int orders = 10;
        ExecutorService pool = Executors.newFixedThreadPool(orders);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < orders; i++) {
            String orderId = "order-" + i;
            pool.submit(() -> {
                start.await();
                reserveWithRedelivery(orderFor(orderId, 1));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        Product product = productPort.findById("p1").orElseThrow();
        assertThat(product.getAvailableQty()).isZero();          // exactly sold out...
        assertThat(product.getReservedQty()).isEqualTo(5);       // ...never below zero
        assertThat(reservations.count()).isEqualTo(5);
        assertThat(recorder.topics.stream().filter(Topics.INVENTORY_RESERVED::equals)).hasSize(5);
        assertThat(recorder.topics.stream().filter(Topics.INVENTORY_FAILED::equals)).hasSize(5);
    }

    @Test
    void redeliveredOrderCreatedReservesOnlyOnce() {
        productPort.save(Product.create("p1", "Keyboard", new java.math.BigDecimal("19.99"), 3, Instant.now()));

        reserveWithRedelivery(orderFor("order-dup", 2));
        reserveWithRedelivery(orderFor("order-dup", 2)); // duplicate delivery

        Product product = productPort.findById("p1").orElseThrow();
        assertThat(product.getAvailableQty()).isEqualTo(1);      // decremented once, not twice
        assertThat(reservations.count()).isEqualTo(1);           // unique orderId index held
        assertThat(recorder.topics).containsExactly(Topics.INVENTORY_RESERVED);
    }
}
