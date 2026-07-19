package com.ecommerce.notification.adapter.in.rest.dto;

import com.ecommerce.notification.domain.model.Channel;
import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String orderId,
        String customerId,
        NotificationType type,
        Channel channel,
        String recipient,
        String content,
        Instant sentAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getOrderId(), n.getCustomerId(),
                n.getType(), n.getChannel(), n.getRecipient(), n.getContent(), n.getSentAt());
    }
}
