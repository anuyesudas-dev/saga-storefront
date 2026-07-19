package com.ecommerce.order.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.order.OrderLine;
import com.ecommerce.common.web.CorrelationContext;
import com.ecommerce.order.application.command.CreateOrderCommand;
import com.ecommerce.order.application.port.EventOutbox;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.port.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    private final OrderRepository orders;
    private final EventOutbox outbox;
    private final Clock clock;
    private final String serviceName;

    public CreateOrderUseCase(OrderRepository orders, EventOutbox outbox, Clock clock,
                              @Value("${spring.application.name}") String serviceName) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
        this.serviceName = serviceName;
    }

    /**
     * Order write and outbox entry are one Mongo transaction — a crash between the
     * two cannot lose the OrderCreated event (ADR-0003).
     */
    @Transactional
    public Order handle(CreateOrderCommand command) {
        List<OrderItem> items = command.items().stream()
                .map(i -> new OrderItem(i.productId(), i.productName(), i.quantity(),
                        Money.of(i.unitPrice(), command.currency())))
                .toList();

        Order order = Order.create(UUID.randomUUID().toString(), command.customerId(), items,
                clock.instant());
        orders.save(order);

        outbox.enqueue(Topics.ORDER_CREATED, order.getId(),
                EventEnvelope.of(EventTypes.ORDER_CREATED, CorrelationContext.currentOrNew(),
                        serviceName, toPayload(order)));

        log.info("Order {} created for customer {} with {} item(s), total {} {}",
                order.getId(), order.getCustomerId(), items.size(),
                order.getTotalAmount().amount(), order.getTotalAmount().currency());
        return order;
    }

    private OrderCreatedPayload toPayload(Order order) {
        List<OrderLine> lines = order.getItems().stream()
                .map(i -> new OrderLine(i.productId(), i.productName(), i.quantity(),
                        i.unitPrice().amount()))
                .toList();
        return new OrderCreatedPayload(order.getId(), order.getCustomerId(), lines,
                order.getTotalAmount().amount(), order.getTotalAmount().currency());
    }
}
