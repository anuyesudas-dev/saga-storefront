package com.ecommerce.inventory.domain.model;

import java.util.Objects;

public record ReservedItem(String productId, int quantity) {

    public ReservedItem {
        Objects.requireNonNull(productId, "productId");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, got: " + quantity);
        }
    }
}
