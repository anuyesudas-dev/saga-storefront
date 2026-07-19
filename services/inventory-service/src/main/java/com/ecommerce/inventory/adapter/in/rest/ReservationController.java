package com.ecommerce.inventory.adapter.in.rest;

import com.ecommerce.inventory.adapter.in.rest.dto.ReservationResponse;
import com.ecommerce.inventory.domain.port.ReservationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Reservation inspection")
public class ReservationController {

    private final ReservationRepository reservations;

    public ReservationController(ReservationRepository reservations) {
        this.reservations = reservations;
    }

    @GetMapping
    @Operation(summary = "Get the reservation for an order")
    public ResponseEntity<ReservationResponse> byOrderId(@RequestParam String orderId) {
        return reservations.findByOrderId(orderId)
                .map(r -> ResponseEntity.ok(ReservationResponse.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }
}
