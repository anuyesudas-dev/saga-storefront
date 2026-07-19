package com.ecommerce.inventory.domain.exception;

import java.util.List;

public class InsufficientStockException extends RuntimeException {

    private final List<String> unavailableProductIds;

    public InsufficientStockException(List<String> unavailableProductIds) {
        super("Insufficient stock for product(s): " + String.join(", ", unavailableProductIds));
        this.unavailableProductIds = List.copyOf(unavailableProductIds);
    }

    public List<String> getUnavailableProductIds() {
        return unavailableProductIds;
    }
}
