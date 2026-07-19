package com.ecommerce.payment.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.payment.PaymentCompletedPayload;
import com.ecommerce.common.events.payment.PaymentFailedPayload;
import com.ecommerce.payment.application.command.ProcessPaymentCommand;
import com.ecommerce.payment.application.port.EventPublisher;
import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.port.PaymentGateway;
import com.ecommerce.payment.domain.port.PaymentGateway.ChargeResult;
import com.ecommerce.payment.domain.port.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Mock
    private PaymentRepository payments;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private EventPublisher publisher;
    @Captor
    private ArgumentCaptor<EventEnvelope<?>> envelopeCaptor;
    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private SimpleMeterRegistry meterRegistry;
    private ProcessPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new ProcessPaymentUseCase(payments, gateway, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry, "payment-service");
    }

    private ProcessPaymentCommand command() {
        return new ProcessPaymentCommand("order-1", "cust-1", new BigDecimal("179.98"), "USD");
    }

    @Test
    void successfulChargePersistsAndPublishesPaymentCompleted() {
        when(payments.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(gateway.charge("order-1", new BigDecimal("179.98"), "USD"))
                .thenReturn(ChargeResult.ok());
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.handle(command());

        verify(payments).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().isCompleted()).isTrue();
        assertThat(paymentCaptor.getValue().getProcessedAt()).isEqualTo(NOW);

        verify(publisher).publish(eq(Topics.PAYMENT_SUCCESS), eq("order-1"),
                envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(EventTypes.PAYMENT_COMPLETED);
        PaymentCompletedPayload payload =
                (PaymentCompletedPayload) envelopeCaptor.getValue().payload();
        assertThat(payload.amount()).isEqualByComparingTo("179.98");
        assertThat(meterRegistry.counter("payment.processed", "outcome", "completed").count())
                .isEqualTo(1.0);
    }

    @Test
    void declinedChargePersistsAndPublishesPaymentFailedWithReason() {
        when(payments.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(gateway.charge(any(), any(), any()))
                .thenReturn(ChargeResult.declined("INSUFFICIENT_FUNDS"));
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.handle(command());

        verify(publisher).publish(eq(Topics.PAYMENT_FAILED), eq("order-1"),
                envelopeCaptor.capture());
        PaymentFailedPayload payload = (PaymentFailedPayload) envelopeCaptor.getValue().payload();
        assertThat(payload.reason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(meterRegistry.counter("payment.processed", "outcome", "failed").count())
                .isEqualTo(1.0);
    }

    @Test
    void redeliveryDoesNotChargeTwiceButRepublishesTheStoredOutcome() {
        Payment stored = Payment.completed("pay-1", "order-1", "cust-1",
                new BigDecimal("179.98"), "USD", NOW);
        when(payments.findByOrderId("order-1")).thenReturn(Optional.of(stored));

        useCase.handle(command());

        verify(gateway, never()).charge(any(), any(), any());
        verify(payments, never()).save(any());
        verify(publisher).publish(eq(Topics.PAYMENT_SUCCESS), eq("order-1"), any());
    }
}
