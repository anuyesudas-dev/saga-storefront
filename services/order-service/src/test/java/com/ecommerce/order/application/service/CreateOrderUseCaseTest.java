package com.ecommerce.order.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.order.application.command.CreateOrderCommand;
import com.ecommerce.order.application.port.EventOutbox;
import com.ecommerce.order.domain.model.Order;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private OrderRepository orders;
    @Mock
    private EventOutbox outbox;
    @Captor
    private ArgumentCaptor<EventEnvelope<?>> envelopeCaptor;

    private CreateOrderUseCase useCase() {
        return new CreateOrderUseCase(orders, outbox, Clock.fixed(NOW, ZoneOffset.UTC), "order-service");
    }

    @Test
    void persistsOrderAndEnqueuesOrderCreatedInOutbox() {
        when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderCommand command = new CreateOrderCommand("cust-1", "USD", List.of(
                new CreateOrderCommand.NewOrderItem("p1", "Keyboard", 2, new BigDecimal("89.99"))));

        Order order = useCase().handle(command);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount().amount()).isEqualByComparingTo("179.98");
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
        verify(orders).save(order);

        verify(outbox).enqueue(
                org.mockito.ArgumentMatchers.eq(Topics.ORDER_CREATED),
                org.mockito.ArgumentMatchers.eq(order.getId()),
                envelopeCaptor.capture());
        EventEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(envelope.source()).isEqualTo("order-service");
        OrderCreatedPayload payload = (OrderCreatedPayload) envelope.payload();
        assertThat(payload.orderId()).isEqualTo(order.getId());
        assertThat(payload.totalAmount()).isEqualByComparingTo("179.98");
        assertThat(payload.items()).hasSize(1);
    }
}
