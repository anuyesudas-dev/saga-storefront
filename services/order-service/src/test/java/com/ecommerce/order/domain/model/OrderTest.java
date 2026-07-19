package com.ecommerce.order.domain.model;

import com.ecommerce.order.domain.exception.IllegalOrderStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    private Order newOrder() {
        return Order.create("order-1", "cust-1", List.of(
                new OrderItem("p1", "Keyboard", 2, Money.of(new BigDecimal("89.99"), "USD")),
                new OrderItem("p2", "Mouse", 1, Money.of(new BigDecimal("20.02"), "USD"))), NOW);
    }

    @Test
    void createComputesTotalFromLineItems() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(new BigDecimal("200.00"), "USD"));
    }

    @Test
    void createRejectsEmptyItems() {
        assertThatThrownBy(() -> Order.create("order-1", "cust-1", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void happyPathTransitions() {
        Order order = newOrder();

        order.markInventoryReserved(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);

        order.markPaid(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatus().isTerminal()).isTrue();
    }

    @Test
    void cannotPayBeforeInventoryIsReserved() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.markPaid(NOW))
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("CREATED").hasMessageContaining("PAID");
    }

    @Test
    void canFailFromCreatedAndFromReserved() {
        Order fromCreated = newOrder();
        fromCreated.markFailed("out of stock", NOW);
        assertThat(fromCreated.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(fromCreated.getStatusReason()).isEqualTo("out of stock");

        Order fromReserved = newOrder();
        fromReserved.markInventoryReserved(NOW);
        fromReserved.markFailed("payment declined", NOW);
        assertThat(fromReserved.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void cancelIsLegalBeforePaymentOnly() {
        Order beforeReservation = newOrder();
        beforeReservation.cancel("changed my mind", NOW);
        assertThat(beforeReservation.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order afterReservation = newOrder();
        afterReservation.markInventoryReserved(NOW);
        afterReservation.cancel("changed my mind", NOW);
        assertThat(afterReservation.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order paid = newOrder();
        paid.markInventoryReserved(NOW);
        paid.markPaid(NOW);
        assertThatThrownBy(() -> paid.cancel("too late", NOW))
                .isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void terminalStatesAcceptNoFurtherTransitions() {
        Order cancelled = newOrder();
        cancelled.cancel("x", NOW);

        assertThatThrownBy(() -> cancelled.markInventoryReserved(NOW))
                .isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(() -> cancelled.markPaid(NOW))
                .isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(() -> cancelled.markFailed("y", NOW))
                .isInstanceOf(IllegalOrderStateException.class);
    }
}
