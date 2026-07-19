package com.ecommerce.notification;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.notification.adapter.out.persistence.NotificationMongoRepository;
import com.ecommerce.notification.application.command.NotifyCommand;
import com.ecommerce.notification.application.port.EventPublisher;
import com.ecommerce.notification.application.service.NotificationDispatcher;
import com.ecommerce.notification.domain.model.NotificationType;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Mongo check that the unique (orderId, type, channel) index — created by
 * auto-index-creation — actually blocks duplicate notifications. This is the
 * test that would catch a missing index configuration, which mocks never could.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class NotificationIdempotencyIT {

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
    private NotificationDispatcher dispatcher;
    @Autowired
    private NotificationMongoRepository notifications;
    @Autowired
    private RecordingPublisher recorder;

    @BeforeEach
    void clean() {
        notifications.deleteAll();
        recorder.topics.clear();
    }

    @Test
    void redeliveredEventNeverNotifiesTheCustomerTwice() {
        NotifyCommand command = new NotifyCommand("order-1", "cust-1",
                NotificationType.ORDER_RECEIVED, null);

        dispatcher.handle(command);
        dispatcher.handle(command); // redelivered event

        // one EMAIL + one SMS — the unique index swallowed the second dispatch
        assertThat(notifications.count()).isEqualTo(2);
        assertThat(notifications.findByOrderId("order-1"))
                .extracting(n -> n.getChannel().name())
                .containsExactlyInAnyOrder("EMAIL", "SMS");
        assertThat(recorder.topics).hasSize(2); // audit events only from the first dispatch
    }
}
