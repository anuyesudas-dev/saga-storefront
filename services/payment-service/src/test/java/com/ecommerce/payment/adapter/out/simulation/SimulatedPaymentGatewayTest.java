package com.ecommerce.payment.adapter.out.simulation;

import com.ecommerce.payment.domain.port.PaymentGateway.ChargeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private SimulatedPaymentGateway gateway(double successRate) {
        return new SimulatedPaymentGateway(
                new PaymentSimulationProperties(successRate, 0), new Random(42));
    }

    @Test
    void amountsEndingIn99AlwaysDecline() {
        ChargeResult result = gateway(1.0).charge("o1", new BigDecimal("19.99"), "USD");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void fullSuccessRateAlwaysCharges() {
        for (int i = 0; i < 20; i++) {
            assertThat(gateway(1.0).charge("o1", new BigDecimal("10.00"), "USD").success()).isTrue();
        }
    }

    @Test
    void zeroSuccessRateAlwaysDeclinesAsCardDeclined() {
        ChargeResult result = gateway(0.0).charge("o1", new BigDecimal("10.00"), "USD");

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void the99RuleLooksAtCentsOnly() {
        assertThat(gateway(1.0).charge("o1", new BigDecimal("99.00"), "USD").success()).isTrue();
        assertThat(gateway(1.0).charge("o1", new BigDecimal("199.99"), "USD").success()).isFalse();
    }
}
