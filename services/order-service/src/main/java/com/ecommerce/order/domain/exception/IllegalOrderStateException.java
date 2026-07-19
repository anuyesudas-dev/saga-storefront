package com.ecommerce.order.domain.exception;

import com.ecommerce.order.domain.model.OrderStatus;

/** Thrown when a status transition is not legal from the order's current state. */
public class IllegalOrderStateException extends RuntimeException {

    private final String orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus attemptedStatus;

    public IllegalOrderStateException(String orderId, OrderStatus current, OrderStatus attempted) {
        super("Order %s cannot transition from %s to %s".formatted(orderId, current, attempted));
        this.orderId = orderId;
        this.currentStatus = current;
        this.attemptedStatus = attempted;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getAttemptedStatus() {
        return attemptedStatus;
    }
}
