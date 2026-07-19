package com.ecommerce.notification.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Insert-only history record — no state machine, no versioning, deliberately so
 * (tactical DDD means not forcing aggregates where a document suffices).
 *
 * <p>The unique compound index (orderId, type, channel) is the idempotency
 * mechanism: a redelivered event trying to record the same notification again
 * hits a duplicate-key error and is skipped — the customer never gets the same
 * email twice.
 */
@Document(collection = "notifications")
@CompoundIndex(name = "uniq_order_type_channel",
        def = "{'orderId': 1, 'type': 1, 'channel': 1}", unique = true)
public class Notification {

    @Id
    private final String id;
    private final String orderId;
    private final String customerId;
    private final NotificationType type;
    private final Channel channel;
    private final String recipient;
    private final String content;
    private final Instant sentAt;

    @PersistenceCreator
    Notification(String id, String orderId, String customerId, NotificationType type,
                 Channel channel, String recipient, String content, Instant sentAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.type = type;
        this.channel = channel;
        this.recipient = recipient;
        this.content = content;
        this.sentAt = sentAt;
    }

    public static Notification sent(String id, String orderId, String customerId,
                                    NotificationType type, Channel channel, String recipient,
                                    String content, Instant now) {
        return new Notification(id, orderId, customerId, type, channel, recipient, content, now);
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public NotificationType getType() {
        return type;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
