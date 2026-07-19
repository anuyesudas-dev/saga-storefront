package com.ecommerce.inventory.application.service;

import com.ecommerce.inventory.domain.exception.IllegalReservationStateException;
import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.model.Reservation;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedItem;
import com.ecommerce.inventory.domain.port.ProductRepository;
import com.ecommerce.inventory.domain.port.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 of the two-phase reservation: PaymentCompleted confirms the hold
 * (stock permanently gone), PaymentFailed / OrderCancelled releases it back.
 * Redeliveries are no-ops; conflicting events follow the same anomaly policy
 * as order-service (ignore, WARN, saga.anomalies metric).
 */
@Service
public class ReservationLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservationLifecycleHandler.class);

    private final ReservationRepository reservations;
    private final ProductRepository products;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ReservationLifecycleHandler(ReservationRepository reservations, ProductRepository products,
                                       Clock clock, MeterRegistry meterRegistry) {
        this.reservations = reservations;
        this.products = products;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void onPaymentCompleted(String orderId) {
        Optional<Reservation> found = reservations.findByOrderId(orderId);
        if (found.isEmpty()) {
            recordAnomaly("PaymentCompleted", "unknown-reservation");
            log.warn("PaymentCompleted for order {} but no reservation exists", orderId);
            return;
        }
        Reservation reservation = found.get();
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            log.info("Reservation for order {} already confirmed — duplicate event, skipping", orderId);
            return;
        }
        Instant now = clock.instant();
        try {
            reservation.confirm(now);
        } catch (IllegalReservationStateException e) {
            // released (cancel/failure won the race) yet payment completed — refund territory
            recordAnomaly("PaymentCompleted", "illegal-transition");
            log.warn("Ignoring PaymentCompleted for order {}: {} — refund required", orderId,
                    e.getMessage());
            return;
        }
        settleProducts(reservation, true, now);
        reservations.save(reservation);
        log.info("Reservation for order {} confirmed — stock permanently deducted", orderId);
    }

    @Transactional
    public void onReservationReleased(String orderId, String cause) {
        Optional<Reservation> found = reservations.findByOrderId(orderId);
        if (found.isEmpty()) {
            // order-cancelled overtook order-created: leave a tombstone so the
            // late OrderCreated cannot reserve stock for a dead order
            reservations.save(Reservation.releasedTombstone(
                    UUID.randomUUID().toString(), orderId, clock.instant()));
            log.info("Release for order {} before any reservation — tombstone placed ({})",
                    orderId, cause);
            return;
        }
        Reservation reservation = found.get();
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("Reservation for order {} already released — duplicate event, skipping", orderId);
            return;
        }
        Instant now = clock.instant();
        try {
            reservation.release(now);
        } catch (IllegalReservationStateException e) {
            recordAnomaly(cause, "illegal-transition");
            log.warn("Ignoring {} for order {}: {}", cause, orderId, e.getMessage());
            return;
        }
        settleProducts(reservation, false, now);
        reservations.save(reservation);
        log.info("Reservation for order {} released back to stock ({})", orderId, cause);
    }

    private void settleProducts(Reservation reservation, boolean confirm, Instant now) {
        for (ReservedItem item : reservation.getItems()) {
            Product product = products.findById(item.productId()).orElseThrow(() ->
                    new IllegalStateException("Reserved product disappeared: " + item.productId()));
            if (confirm) {
                product.confirmReservation(item.quantity(), now);
            } else {
                product.releaseReservation(item.quantity(), now);
            }
            products.save(product);
        }
    }

    private void recordAnomaly(String eventType, String cause) {
        meterRegistry.counter("saga.anomalies", "event", eventType, "cause", cause).increment();
    }
}
