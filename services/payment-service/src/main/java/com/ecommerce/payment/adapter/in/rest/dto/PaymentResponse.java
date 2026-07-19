package com.ecommerce.payment.adapter.in.rest.dto;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant processedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getCustomerId(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus(),
                payment.getFailureReason(), payment.getProcessedAt());
    }
}
