package com.ecommerce.common.events.order;

public record OrderCancelledPayload(
        String orderId,
        String customerId,
        String reason) {
}
