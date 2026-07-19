package com.ecommerce.common.events.inventory;

public record ReservedLine(
        String productId,
        int quantity) {
}
