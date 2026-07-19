package com.ecommerce.inventory.application.command;

import java.math.BigDecimal;
import java.util.List;

/** Derived from an OrderCreated event by the Kafka listener adapter. */
public record ReserveInventoryCommand(
        String orderId,
        String customerId,
        List<Line> items,
        BigDecimal totalAmount,
        String currency) {

    public record Line(String productId, int quantity) {
    }
}
