package com.ecommerce.notification.application.command;

import com.ecommerce.notification.domain.model.NotificationType;

/**
 * Derived from saga events by the Kafka listener adapter.
 * {@code detail} feeds the message template (failure reason, amount, ...).
 */
public record NotifyCommand(
        String orderId,
        String customerId,
        NotificationType type,
        String detail) {
}
