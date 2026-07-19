package com.ecommerce.payment.adapter.in.messaging;

import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.common.kafka.IdempotentEventProcessor;
import com.ecommerce.payment.application.command.ProcessPaymentCommand;
import com.ecommerce.payment.application.service.ProcessPaymentUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventsListener {

    private final IdempotentEventProcessor processor;
    private final ProcessPaymentUseCase processPayment;

    public PaymentEventsListener(IdempotentEventProcessor processor,
                                 ProcessPaymentUseCase processPayment) {
        this.processor = processor;
        this.processPayment = processPayment;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED)
    public void onInventoryReserved(String message) {
        processor.process(message, InventoryReservedPayload.class, env -> {
            InventoryReservedPayload p = env.payload();
            // the amount rides in on the event — event-carried state transfer (ADR-0004)
            processPayment.handle(new ProcessPaymentCommand(
                    p.orderId(), p.customerId(), p.totalAmount(), p.currency()));
        });
    }
}
