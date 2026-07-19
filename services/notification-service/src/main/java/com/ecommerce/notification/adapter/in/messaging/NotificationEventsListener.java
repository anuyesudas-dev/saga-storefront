package com.ecommerce.notification.adapter.in.messaging;

import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservationFailedPayload;
import com.ecommerce.common.events.order.OrderCancelledPayload;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.payment.PaymentFailedPayload;
import com.ecommerce.common.kafka.IdempotentEventProcessor;
import com.ecommerce.notification.application.command.NotifyCommand;
import com.ecommerce.notification.application.service.NotificationDispatcher;
import com.ecommerce.notification.domain.model.NotificationType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Maps every customer-relevant saga event to a notification. */
@Component
public class NotificationEventsListener {

    private final IdempotentEventProcessor processor;
    private final NotificationDispatcher dispatcher;

    public NotificationEventsListener(IdempotentEventProcessor processor,
                                      NotificationDispatcher dispatcher) {
        this.processor = processor;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(String message) {
        processor.process(message, OrderCreatedPayload.class,
                env -> dispatcher.handle(new NotifyCommand(env.payload().orderId(),
                        env.payload().customerId(), NotificationType.ORDER_RECEIVED, null)));
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED)
    public void onInventoryReservationFailed(String message) {
        processor.process(message, InventoryReservationFailedPayload.class,
                env -> dispatcher.handle(new NotifyCommand(env.payload().orderId(),
                        env.payload().customerId(), NotificationType.ORDER_FAILED,
                        env.payload().reason())));
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCESS)
    public void onPaymentCompleted(String message) {
        processor.process(message, PaymentCompletedPayload.class,
                env -> dispatcher.handle(new NotifyCommand(env.payload().orderId(),
                        env.payload().customerId(), NotificationType.PAYMENT_RECEIPT,
                        env.payload().amount() + " " + env.payload().currency())));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String message) {
        processor.process(message, PaymentFailedPayload.class,
                env -> dispatcher.handle(new NotifyCommand(env.payload().orderId(),
                        env.payload().customerId(), NotificationType.PAYMENT_FAILED,
                        env.payload().reason())));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(String message) {
        processor.process(message, OrderCancelledPayload.class,
                env -> dispatcher.handle(new NotifyCommand(env.payload().orderId(),
                        env.payload().customerId(), NotificationType.ORDER_CANCELLED,
                        env.payload().reason())));
    }
}
