package com.ecommerce.order.domain.model;

import java.util.Objects;

/** Line item — a value object with no identity outside its Order. */
public record OrderItem(String productId, String productName, int quantity, Money unitPrice) {

    public OrderItem {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(productName, "productName");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, got: " + quantity);
        }
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
