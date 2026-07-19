package com.ecommerce.payment.application.command;

import java.math.BigDecimal;

/** Derived from an InventoryReserved event by the Kafka listener adapter. */
public record ProcessPaymentCommand(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency) {
}
