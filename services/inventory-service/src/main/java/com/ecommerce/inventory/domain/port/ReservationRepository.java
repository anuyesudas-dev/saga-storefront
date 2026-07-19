package com.ecommerce.inventory.domain.port;

import com.ecommerce.inventory.domain.model.Reservation;

import java.util.Optional;

public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findByOrderId(String orderId);
}
