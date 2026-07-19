package com.ecommerce.common.events.notification;

public record NotificationSentPayload(
        String orderId,
        String customerId,
        String notificationType,
        String channel,
        String status) {
}
