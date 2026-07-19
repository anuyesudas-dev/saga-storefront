package com.ecommerce.common.events.payment;

import java.math.BigDecimal;

public record PaymentCompletedPayload(
        String orderId,
        String customerId,
        String paymentId,
        BigDecimal amount,
        String currency) {
}
