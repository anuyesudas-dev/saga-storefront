package com.ecommerce.inventory.application.service;

import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.model.Reservation;
import com.ecommerce.inventory.domain.model.ReservationStatus;
import com.ecommerce.inventory.domain.model.ReservedItem;
import com.ecommerce.inventory.domain.port.ProductRepository;
import com.ecommerce.inventory.domain.port.ReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationLifecycleHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private ReservationRepository reservations;
    @Mock
    private ProductRepository products;
    private SimpleMeterRegistry meterRegistry;
    private ReservationLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new ReservationLifecycleHandler(reservations, products,
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
    }

    private Product heldProduct() {
        Product product = Product.create("p1", "Keyboard", 10, NOW);
        product.reserve(2, NOW);
        return product;
    }

    private Reservation activeReservation() {
        return Reservation.active("r1", "order-1", List.of(new ReservedItem("p1", 2)), NOW);
    }

    @Test
    void paymentCompletedConfirmsReservationAndDeductsPermanently() {
        Reservation reservation = activeReservation();
        Product product = heldProduct();
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(products.findById("p1")).thenReturn(Optional.of(product));

        handler.onPaymentCompleted("order-1");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(product.getReservedQty()).isZero();
        assertThat(product.getAvailableQty()).isEqualTo(8); // stock permanently gone
        verify(products).save(product);
        verify(reservations).save(reservation);
    }

    @Test
    void paymentFailureReleasesStockBackToAvailable() {
        Reservation reservation = activeReservation();
        Product product = heldProduct();
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(products.findById("p1")).thenReturn(Optional.of(product));

        handler.onReservationReleased("order-1", "PaymentFailed");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(product.getAvailableQty()).isEqualTo(10); // compensation restored stock
        assertThat(product.getReservedQty()).isZero();
    }

    @Test
    void duplicateConfirmIsIdempotentNoOp() {
        Reservation reservation = activeReservation();
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.of(reservation));
        when(products.findById("p1")).thenReturn(Optional.of(heldProduct()));
        handler.onPaymentCompleted("order-1");

        handler.onPaymentCompleted("order-1"); // redelivery

        verify(reservations, org.mockito.Mockito.times(1)).save(reservation);
    }

    @Test
    void paymentCompletedAfterReleaseIsAnomalyNotCrash() {
        Reservation released = activeReservation();
        released.release(NOW);
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.of(released));

        handler.onPaymentCompleted("order-1");

        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(products, never()).findById(any());
        assertThat(meterRegistry.counter("saga.anomalies",
                "event", "PaymentCompleted", "cause", "illegal-transition").count()).isEqualTo(1.0);
    }

    @Test
    void releaseBeforeReservationExistsPlacesTombstone() {
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.empty());

        handler.onReservationReleased("order-1", "OrderCancelled");

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(captor.getValue().getItems()).isEmpty();
    }
}
