package com.ecommerce.inventory.adapter.in.messaging;

import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.order.OrderCancelledPayload;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.payment.PaymentFailedPayload;
import com.ecommerce.common.kafka.IdempotentEventProcessor;
import com.ecommerce.inventory.application.command.ReserveInventoryCommand;
import com.ecommerce.inventory.application.service.ReservationLifecycleHandler;
import com.ecommerce.inventory.application.service.ReserveInventoryUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryEventsListener {

    private final IdempotentEventProcessor processor;
    private final ReserveInventoryUseCase reserve;
    private final ReservationLifecycleHandler lifecycle;

    public InventoryEventsListener(IdempotentEventProcessor processor,
                                   ReserveInventoryUseCase reserve,
                                   ReservationLifecycleHandler lifecycle) {
        this.processor = processor;
        this.reserve = reserve;
        this.lifecycle = lifecycle;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(String message) {
        processor.process(message, OrderCreatedPayload.class, env -> {
            OrderCreatedPayload p = env.payload();
            List<ReserveInventoryCommand.Line> lines = p.items().stream()
                    .map(i -> new ReserveInventoryCommand.Line(i.productId(), i.quantity()))
                    .toList();
            reserve.handle(new ReserveInventoryCommand(p.orderId(), p.customerId(), lines,
                    p.totalAmount(), p.currency()));
        });
    }

    @KafkaListener(topics = Topics.PAYMENT_SUCCESS)
    public void onPaymentCompleted(String message) {
        processor.process(message, PaymentCompletedPayload.class,
                env -> lifecycle.onPaymentCompleted(env.payload().orderId()));
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED)
    public void onPaymentFailed(String message) {
        processor.process(message, PaymentFailedPayload.class,
                env -> lifecycle.onReservationReleased(env.payload().orderId(), "PaymentFailed"));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    public void onOrderCancelled(String message) {
        processor.process(message, OrderCancelledPayload.class,
                env -> lifecycle.onReservationReleased(env.payload().orderId(), "OrderCancelled"));
    }
}
