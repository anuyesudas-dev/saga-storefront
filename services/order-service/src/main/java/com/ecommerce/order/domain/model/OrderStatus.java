package com.ecommerce.order.domain.model;

public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVED,
    PAID,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED || this == CANCELLED;
    }
}
