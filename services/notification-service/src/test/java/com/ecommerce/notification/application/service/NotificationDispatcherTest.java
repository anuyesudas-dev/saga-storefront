package com.ecommerce.notification.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.notification.NotificationSentPayload;
import com.ecommerce.notification.application.command.NotifyCommand;
import com.ecommerce.notification.application.port.EventPublisher;
import com.ecommerce.notification.adapter.out.channel.SimulatedEmailChannel;
import com.ecommerce.notification.adapter.out.channel.SimulatedSmsChannel;
import com.ecommerce.notification.domain.model.Channel;
import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.model.NotificationType;
import com.ecommerce.notification.domain.port.NotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private NotificationRepository notifications;
    @Mock
    private EventPublisher publisher;
    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;
    @Captor
    private ArgumentCaptor<EventEnvelope<?>> envelopeCaptor;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(
                List.of(new SimulatedEmailChannel(), new SimulatedSmsChannel()),
                notifications, publisher, Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry(), "notification-service");
    }

    @Test
    void sendsOnEveryChannelAndRecordsHistory() {
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.handle(new NotifyCommand("order-1", "cust-1",
                NotificationType.ORDER_RECEIVED, null));

        verify(notifications, times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting(Notification::getChannel)
                .containsExactly(Channel.EMAIL, Channel.SMS);
        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(n -> {
                    assertThat(n.getContent()).contains("order-1");
                    assertThat(n.getSentAt()).isEqualTo(NOW);
                });
        assertThat(notificationCaptor.getAllValues().get(0).getRecipient())
                .isEqualTo("cust-1@example.com");

        verify(publisher, times(2)).publish(eq(Topics.NOTIFICATION_SENT), eq("order-1"),
                envelopeCaptor.capture());
        NotificationSentPayload payload =
                (NotificationSentPayload) envelopeCaptor.getAllValues().get(0).payload();
        assertThat(payload.notificationType()).isEqualTo("ORDER_RECEIVED");
        assertThat(payload.status()).isEqualTo("SENT");
    }

    @Test
    void duplicateDeliveryIsSkippedPerChannelWithoutPublishing() {
        when(notifications.save(any()))
                .thenThrow(new DuplicateKeyException("uniq_order_type_channel"))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatcher.handle(new NotifyCommand("order-1", "cust-1",
                NotificationType.PAYMENT_RECEIPT, "179.98 USD"));

        // email insert hit the unique index (already sent) — only SMS goes out
        verify(publisher, times(1)).publish(eq(Topics.NOTIFICATION_SENT), eq("order-1"),
                envelopeCaptor.capture());
        NotificationSentPayload payload =
                (NotificationSentPayload) envelopeCaptor.getValue().payload();
        assertThat(payload.channel()).isEqualTo("SMS");
    }
}
