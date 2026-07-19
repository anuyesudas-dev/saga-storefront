package com.ecommerce.order.application.service;

import com.ecommerce.order.domain.exception.IllegalOrderStateException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Applies downstream saga events to the order state machine.
 *
 * <p>Anomaly policy (ADR-0001 / Phase 3): the first legal transition wins. A
 * conflicting later event (e.g. PaymentCompleted arriving for an order already
 * CANCELLED) is NOT applied — it is logged at WARN and counted in the
 * {@code saga.anomalies} metric, which in production would page a human
 * (e.g. "refund required"). Optimistic locking on Order makes this safe under
 * concurrent consumption.
 */
@Service
public class OrderSagaEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaEventHandler.class);

    private final OrderRepository orders;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OrderSagaEventHandler(OrderRepository orders, Clock clock, MeterRegistry meterRegistry) {
        this.orders = orders;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void onInventoryReserved(String orderId) {
        apply(orderId, "InventoryReserved", o -> o.markInventoryReserved(clock.instant()));
    }

    @Transactional
    public void onInventoryReservationFailed(String orderId, String reason) {
        apply(orderId, "InventoryReservationFailed", o -> o.markFailed(reason, clock.instant()));
    }

    @Transactional
    public void onPaymentCompleted(String orderId) {
        apply(orderId, "PaymentCompleted", o -> o.markPaid(clock.instant()));
    }

    @Transactional
    public void onPaymentFailed(String orderId, String reason) {
        apply(orderId, "PaymentFailed", o -> o.markFailed(reason, clock.instant()));
    }

    private void apply(String orderId, String eventType, Consumer<Order> transition) {
        Optional<Order> found = orders.findById(orderId);
        if (found.isEmpty()) {
            recordAnomaly(eventType, "unknown-order");
            log.warn("Received {} for unknown order {} — recorded as anomaly", eventType, orderId);
            return;
        }
        Order order = found.get();
        try {
            transition.accept(order);
        } catch (IllegalOrderStateException e) {
            recordAnomaly(eventType, "illegal-transition");
            log.warn("Ignoring {} for order {}: {} (first legal transition wins)",
                    eventType, orderId, e.getMessage());
            return;
        }
        orders.save(order);
        log.info("Order {} transitioned to {} on {}", orderId, order.getStatus(), eventType);
    }

    private void recordAnomaly(String eventType, String cause) {
        meterRegistry.counter("saga.anomalies", "event", eventType, "cause", cause).increment();
    }
}
