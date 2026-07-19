package com.ecommerce.order.adapter.in.messaging;

import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservationFailedPayload;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.payment.PaymentFailedPayload;
import com.ecommerce.common.kafka.IdempotentEventProcessor;
import com.ecommerce.order.application.service.OrderSagaEventHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds downstream saga events into the order state machine. Thin by design:
 * deserialization/idempotency/correlation live in the processor, decisions in
 * the application layer.
 */
@Component
public class OrderSagaEventsListener {

    private final IdempotentEventProcessor processor;
    private final OrderSagaEventHandler saga;

    public OrderSagaEventsListener(IdempotentEventProcessor processor, OrderSagaEventHandler saga) {
        this.processor = processor;
        this.saga = saga;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED)
    public void onInventoryReserved(String message) {
        processor.process(message, InventoryReservedPayload.class,
                env -> saga.onInventoryReserved(env.payload().orderId()));
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED)
    public void onInventoryReservationFailed(String message) {
        processor.process(message, InventoryReservationFailedPayload.class,
                env -> saga.onInventoryReservationFailed(
                        env.payload().orderId(), env.payload().reason()));
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCESS)
    public void onPaymentCompleted(String message) {
        processor.process(message, PaymentCompletedPayload.class,
                env -> saga.onPaymentCompleted(env.payload().orderId()));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String message) {
        processor.process(message, PaymentFailedPayload.class,
                env -> saga.onPaymentFailed(env.payload().orderId(), env.payload().reason()));
    }
}
