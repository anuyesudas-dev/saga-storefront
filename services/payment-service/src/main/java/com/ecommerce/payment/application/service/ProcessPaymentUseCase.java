package com.ecommerce.payment.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.payment.PaymentFailedPayload;
import com.ecommerce.common.web.CorrelationContext;
import com.ecommerce.payment.application.command.ProcessPaymentCommand;
import com.ecommerce.payment.application.port.EventPublisher;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.port.PaymentGateway;
import com.ecommerce.payment.domain.port.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles InventoryReserved: charge (simulated), persist the outcome, publish it.
 *
 * <p>Idempotency: the payments collection has a unique orderId index, and a
 * redelivered event finds the existing payment and republishes ITS outcome
 * rather than charging twice — republishing is safe (downstream consumers
 * dedupe), silently swallowing a possibly-unpublished outcome is not.
 */
@Service
public class ProcessPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentUseCase.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final EventPublisher publisher;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public ProcessPaymentUseCase(PaymentRepository payments, PaymentGateway gateway,
                                 EventPublisher publisher, Clock clock, MeterRegistry meterRegistry,
                                 @Value("${spring.application.name}") String serviceName) {
        this.payments = payments;
        this.gateway = gateway;
        this.publisher = publisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;
    }

    public void handle(ProcessPaymentCommand command) {
        Optional<Payment> existing = payments.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            log.info("Payment for order {} already processed ({}) — republishing outcome",
                    command.orderId(), existing.get().getStatus());
            publishOutcome(existing.get());
            return;
        }

        PaymentGateway.ChargeResult result =
                gateway.charge(command.orderId(), command.amount(), command.currency());

        Payment payment = result.success()
                ? Payment.completed(UUID.randomUUID().toString(), command.orderId(),
                command.customerId(), command.amount(), command.currency(), clock.instant())
                : Payment.failed(UUID.randomUUID().toString(), command.orderId(),
                command.customerId(), command.amount(), command.currency(),
                result.failureReason(), clock.instant());

        try {
            payments.save(payment);
        } catch (DuplicateKeyException e) {
            // concurrent redelivery charged first — defer to the stored outcome
            log.info("Concurrent duplicate payment for order {} — republishing stored outcome",
                    command.orderId());
            payments.findByOrderId(command.orderId()).ifPresent(this::publishOutcome);
            return;
        }

        meterRegistry.counter("payment.processed",
                "outcome", payment.isCompleted() ? "completed" : "failed").increment();
        log.info("Payment for order {} {} ({} {}){}", command.orderId(),
                payment.isCompleted() ? "completed" : "FAILED",
                command.amount(), command.currency(),
                payment.isCompleted() ? "" : ": " + payment.getFailureReason());
        publishOutcome(payment);
    }

    private void publishOutcome(Payment payment) {
        if (payment.isCompleted()) {
            publisher.publish(Topics.PAYMENT_SUCCESS, payment.getOrderId(),
                    envelope(EventTypes.PAYMENT_COMPLETED, new PaymentCompletedPayload(
                            payment.getOrderId(), payment.getCustomerId(), payment.getId(),
                            payment.getAmount(), payment.getCurrency())));
        } else {
            publisher.publish(Topics.PAYMENT_FAILED, payment.getOrderId(),
                    envelope(EventTypes.PAYMENT_FAILED, new PaymentFailedPayload(
                            payment.getOrderId(), payment.getCustomerId(), payment.getId(),
                            payment.getAmount(), payment.getCurrency(), payment.getFailureReason())));
        }
    }

    private EventEnvelope<?> envelope(String eventType, Object payload) {
        return EventEnvelope.of(eventType, CorrelationContext.currentOrNew(), serviceName, payload);
    }
}
