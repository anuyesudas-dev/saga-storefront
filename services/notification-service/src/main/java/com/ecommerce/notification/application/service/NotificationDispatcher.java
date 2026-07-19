package com.ecommerce.notification.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.notification.NotificationSentPayload;
import com.ecommerce.common.web.CorrelationContext;
import com.ecommerce.notification.application.command.NotifyCommand;
import com.ecommerce.notification.application.port.EventPublisher;
import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.port.NotificationChannel;
import com.ecommerce.notification.domain.port.NotificationRepository;
import com.ecommerce.notification.domain.service.NotificationComposer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Sends one notification per registered channel and records each in history.
 *
 * <p>Idempotency: the unique (orderId, type, channel) index makes the history
 * insert the gate — on a redelivered event the insert fails with a duplicate
 * key, and that channel is skipped, so customers never receive the same
 * notification twice.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;
    private final NotificationRepository notifications;
    private final EventPublisher publisher;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public NotificationDispatcher(List<NotificationChannel> channels,
                                  NotificationRepository notifications,
                                  EventPublisher publisher, Clock clock,
                                  MeterRegistry meterRegistry,
                                  @Value("${spring.application.name}") String serviceName) {
        this.channels = channels;
        this.notifications = notifications;
        this.publisher = publisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;
    }

    public void handle(NotifyCommand command) {
        String content = NotificationComposer.compose(
                command.type(), command.orderId(), command.detail());

        for (NotificationChannel channel : channels) {
            String recipient = channel.recipientFor(command.customerId());
            Notification notification = Notification.sent(
                    UUID.randomUUID().toString(), command.orderId(), command.customerId(),
                    command.type(), channel.channel(), recipient, content, clock.instant());
            try {
                notifications.save(notification);
            } catch (DuplicateKeyException e) {
                log.info("Notification {}/{} for order {} already sent — skipping duplicate",
                        command.type(), channel.channel(), command.orderId());
                continue;
            }

            channel.send(recipient, content);
            meterRegistry.counter("notifications.sent",
                    "type", command.type().name(), "channel", channel.channel().name()).increment();

            publisher.publish(Topics.NOTIFICATION_SENT, command.orderId(),
                    EventEnvelope.of(EventTypes.NOTIFICATION_SENT,
                            CorrelationContext.currentOrNew(), serviceName,
                            new NotificationSentPayload(command.orderId(), command.customerId(),
                                    command.type().name(), channel.channel().name(), "SENT")));
        }
    }
}
