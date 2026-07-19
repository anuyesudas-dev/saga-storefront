package com.ecommerce.inventory.domain.exception;

import com.ecommerce.inventory.domain.model.ReservationStatus;

public class IllegalReservationStateException extends RuntimeException {

    private final ReservationStatus currentStatus;

    public IllegalReservationStateException(String orderId, ReservationStatus current,
                                            ReservationStatus attempted) {
        super("Reservation for order %s cannot transition from %s to %s"
                .formatted(orderId, current, attempted));
        this.currentStatus = current;
    }

    public ReservationStatus getCurrentStatus() {
        return currentStatus;
    }
}
