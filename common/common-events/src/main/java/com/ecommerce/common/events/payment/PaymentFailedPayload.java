package com.ecommerce.common.events.payment;

import java.math.BigDecimal;

public record PaymentFailedPayload(
        String orderId,
        String customerId,
        String paymentId,
        BigDecimal amount,
        String currency,
        String reason) {
}
