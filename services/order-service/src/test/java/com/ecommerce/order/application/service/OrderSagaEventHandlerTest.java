package com.ecommerce.order.application.service;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class OrderSagaEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private OrderRepository orders;
    private SimpleMeterRegistry meterRegistry;
    private OrderSagaEventHandler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new OrderSagaEventHandler(orders, Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
    }

    private Order createdOrder() {
        return Order.create("order-1", "cust-1",
                List.of(new OrderItem("p1", "Keyboard", 1, Money.of(new BigDecimal("10.00"), "USD"))),
                NOW);
    }

    @Test
    void appliesSagaEventsThroughTheStateMachine() {
        Order order = createdOrder();
        when(orders.findById("order-1")).thenReturn(Optional.of(order));

        handler.onInventoryReserved("order-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);

        handler.onPaymentCompleted("order-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orders, org.mockito.Mockito.times(2)).save(order);
    }

    @Test
    void paymentFailureMarksOrderFailedWithReason() {
        Order order = createdOrder();
        order.markInventoryReserved(NOW);
        when(orders.findById("order-1")).thenReturn(Optional.of(order));

        handler.onPaymentFailed("order-1", "INSUFFICIENT_FUNDS");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getStatusReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void conflictingEventIsIgnoredAndRecordedAsAnomaly() {
        // user cancelled while payment was in flight — PaymentCompleted arrives late
        Order cancelled = createdOrder();
        cancelled.markInventoryReserved(NOW);
        cancelled.cancel("changed my mind", NOW);
        when(orders.findById("order-1")).thenReturn(Optional.of(cancelled));

        handler.onPaymentCompleted("order-1");

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED); // first legal transition won
        verify(orders, never()).save(any());
        double anomalies = meterRegistry.counter("saga.anomalies",
                "event", "PaymentCompleted", "cause", "illegal-transition").count();
        assertThat(anomalies).isEqualTo(1.0);
    }

    @Test
    void eventForUnknownOrderIsRecordedAsAnomaly() {
        when(orders.findById("ghost")).thenReturn(Optional.empty());

        handler.onInventoryReserved("ghost");

        verify(orders, never()).save(any());
        double anomalies = meterRegistry.counter("saga.anomalies",
                "event", "InventoryReserved", "cause", "unknown-order").count();
        assertThat(anomalies).isEqualTo(1.0);
    }
}
