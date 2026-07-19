package com.ecommerce.order.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.order.OrderCancelledPayload;
import com.ecommerce.common.web.CorrelationContext;
import com.ecommerce.order.application.port.EventOutbox;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderUseCase.class);

    private final OrderRepository orders;
    private final EventOutbox outbox;
    private final Clock clock;
    private final String serviceName;

    public CancelOrderUseCase(OrderRepository orders, EventOutbox outbox, Clock clock,
                              @Value("${spring.application.name}") String serviceName) {
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
        this.serviceName = serviceName;
    }

    @Transactional
    public Order handle(String orderId, String reason) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.cancel(reason, clock.instant());
        orders.save(order);

        outbox.enqueue(Topics.ORDER_CANCELLED, order.getId(),
                EventEnvelope.of(EventTypes.ORDER_CANCELLED, CorrelationContext.currentOrNew(),
                        serviceName,
                        new OrderCancelledPayload(order.getId(), order.getCustomerId(), reason)));

        log.info("Order {} cancelled: {}", orderId, reason);
        return order;
    }
}
