package com.ecommerce.order.application.service;

import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.port.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private OrderRepository orders;

    private Order createdOrder() {
        return Order.create("order-1", "cust-1",
                List.of(new OrderItem("p1", "Keyboard", 1, Money.of(new BigDecimal("10.00"), "USD"))),
                NOW);
    }

    @Test
    void ownerCanFetchTheirOwnOrder() {
        when(orders.findById("order-1")).thenReturn(Optional.of(createdOrder()));

        Order result = new OrderQueryService(orders).getById("order-1", "cust-1");

        assertThat(result.getId()).isEqualTo("order-1");
    }

    @Test
    void nonOwnerGetsNotFoundNotForbidden() {
        when(orders.findById("order-1")).thenReturn(Optional.of(createdOrder()));

        assertThatThrownBy(() -> new OrderQueryService(orders).getById("order-1", "someone-else"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void noOwnerFilterReturnsAnyOrder() {
        when(orders.findById("order-1")).thenReturn(Optional.of(createdOrder()));

        Order result = new OrderQueryService(orders).getById("order-1");

        assertThat(result.getId()).isEqualTo("order-1");
    }
}
