package com.ecommerce.notification.domain.service;

import com.ecommerce.notification.domain.model.NotificationType;

/** Pure message templating — no I/O, trivially unit-testable. */
public final class NotificationComposer {

    private NotificationComposer() {
    }

    public static String compose(NotificationType type, String orderId, String detail) {
        return switch (type) {
            case ORDER_RECEIVED -> "Your order %s has been received and is being processed."
                    .formatted(orderId);
            case ORDER_FAILED -> "Unfortunately your order %s could not be completed: %s"
                    .formatted(orderId, detail);
            case PAYMENT_RECEIPT -> "Payment received for order %s (%s). Thank you for shopping with us!"
                    .formatted(orderId, detail);
            case PAYMENT_FAILED -> "Payment for order %s failed: %s. Please try again with a different method."
                    .formatted(orderId, detail);
            case ORDER_CANCELLED -> "Your order %s has been cancelled as requested."
                    .formatted(orderId);
        };
    }
}
