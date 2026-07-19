package com.ecommerce.order.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.order.application.port.EventOutbox;
import com.ecommerce.order.domain.exception.IllegalOrderStateException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private OrderRepository orders;
    @Mock
    private EventOutbox outbox;
    @Captor
    private ArgumentCaptor<EventEnvelope<?>> envelopeCaptor;

    private CancelOrderUseCase useCase() {
        return new CancelOrderUseCase(orders, outbox, Clock.fixed(NOW, ZoneOffset.UTC), "order-service");
    }

    private Order createdOrder() {
        return Order.create("order-1", "cust-1",
                List.of(new OrderItem("p1", "Keyboard", 1, Money.of(new BigDecimal("10.00"), "USD"))),
                NOW);
    }

    @Test
    void cancelsAndEnqueuesOrderCancelled() {
        Order order = createdOrder();
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = useCase().handle("order-1", "changed my mind");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox).enqueue(eq(Topics.ORDER_CANCELLED), eq("order-1"), envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(EventTypes.ORDER_CANCELLED);
    }

    @Test
    void unknownOrderThrowsNotFoundAndPublishesNothing() {
        when(orders.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().handle("missing", "x"))
                .isInstanceOf(OrderNotFoundException.class);
        verify(outbox, never()).enqueue(any(), any(), any());
    }

    @Test
    void cancellingPaidOrderThrowsConflictAndPublishesNothing() {
        Order paid = createdOrder();
        paid.markInventoryReserved(NOW);
        paid.markPaid(NOW);
        when(orders.findById("order-1")).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> useCase().handle("order-1", "too late"))
                .isInstanceOf(IllegalOrderStateException.class);
        verify(orders, never()).save(any());
        verify(outbox, never()).enqueue(any(), any(), any());
    }
}
