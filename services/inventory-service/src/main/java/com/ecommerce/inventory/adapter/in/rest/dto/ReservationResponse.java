package com.ecommerce.inventory.adapter.in.rest.dto;

import com.ecommerce.inventory.domain.model.Reservation;
import com.ecommerce.inventory.domain.model.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        String id,
        String orderId,
        List<Line> items,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(String productId, int quantity) {
    }

    public static ReservationResponse from(Reservation reservation) {
        List<Line> lines = reservation.getItems().stream()
                .map(i -> new Line(i.productId(), i.quantity()))
                .toList();
        return new ReservationResponse(reservation.getId(), reservation.getOrderId(), lines,
                reservation.getStatus(), reservation.getCreatedAt(), reservation.getUpdatedAt());
    }
}
