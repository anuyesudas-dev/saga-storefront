package com.ecommerce.inventory.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservationFailedPayload;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.inventory.application.command.ReserveInventoryCommand;
import com.ecommerce.inventory.application.port.EventPublisher;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveInventoryUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private ProductRepository products;
    @Mock
    private ReservationRepository reservations;
    @Mock
    private EventPublisher publisher;
    @Mock
    private TransactionTemplate transaction;
    @Captor
    private ArgumentCaptor<EventEnvelope<?>> envelopeCaptor;

    private ReserveInventoryUseCase useCase;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        // make the template execute its callback inline — we unit-test logic, not Mongo tx
        lenient().when(transaction.execute(any())).thenAnswer(inv ->
                ((TransactionCallback) inv.getArgument(0)).doInTransaction(null));
        useCase = new ReserveInventoryUseCase(products, reservations, publisher, transaction,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry(), "inventory-service");
    }

    private ReserveInventoryCommand command() {
        return new ReserveInventoryCommand("order-1", "cust-1",
                List.of(new ReserveInventoryCommand.Line("p1", 2),
                        new ReserveInventoryCommand.Line("p2", 1)),
                new BigDecimal("179.98"), "USD");
    }

    @Test
    void reservesAllProductsAndPublishesInventoryReserved() {
        Product p1 = Product.create("p1", "Keyboard", new java.math.BigDecimal("19.99"), 10, NOW);
        Product p2 = Product.create("p2", "Mouse", new java.math.BigDecimal("19.99"), 5, NOW);
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(products.findById("p1")).thenReturn(Optional.of(p1));
        when(products.findById("p2")).thenReturn(Optional.of(p2));
        when(reservations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.handle(command());

        assertThat(p1.getAvailableQty()).isEqualTo(8);
        assertThat(p1.getReservedQty()).isEqualTo(2);
        assertThat(p2.getAvailableQty()).isEqualTo(4);

        verify(publisher).publish(eq(Topics.INVENTORY_RESERVED), eq("order-1"),
                envelopeCaptor.capture());
        EventEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.INVENTORY_RESERVED);
        InventoryReservedPayload payload = (InventoryReservedPayload) envelope.payload();
        assertThat(payload.totalAmount()).isEqualByComparingTo("179.98"); // enrichment carried through
        assertThat(payload.items()).hasSize(2);
    }

    @Test
    void insufficientStockPublishesFailureNamingEveryUnavailableProduct() {
        Product p1 = Product.create("p1", "Keyboard", new java.math.BigDecimal("19.99"), 1, NOW); // needs 2
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(products.findById("p1")).thenReturn(Optional.of(p1));
        when(products.findById("p2")).thenReturn(Optional.empty()); // unknown product

        useCase.handle(command());

        verify(reservations, never()).save(any());
        verify(products, never()).save(any());
        verify(publisher).publish(eq(Topics.INVENTORY_FAILED), eq("order-1"),
                envelopeCaptor.capture());
        InventoryReservationFailedPayload payload =
                (InventoryReservationFailedPayload) envelopeCaptor.getValue().payload();
        assertThat(payload.unavailableProductIds()).containsExactly("p1", "p2");
    }

    @Test
    void existingReservationMakesRedeliveryANoOp() {
        Reservation existing = Reservation.active("r1", "order-1",
                List.of(new ReservedItem("p1", 2)), NOW);
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.of(existing));

        useCase.handle(command());

        verify(products, never()).findById(any());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void tombstoneFromEarlierCancellationBlocksReservation() {
        // order-cancelled overtook order-created; release left a RELEASED tombstone
        when(reservations.findByOrderId("order-1")).thenReturn(Optional.of(
                Reservation.releasedTombstone("r1", "order-1", NOW)));

        useCase.handle(command());

        verify(products, never()).findById(any());
        verify(publisher, never()).publish(any(), any(), any());
        assertThat(Reservation.releasedTombstone("r1", "order-1", NOW).getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
    }
}
