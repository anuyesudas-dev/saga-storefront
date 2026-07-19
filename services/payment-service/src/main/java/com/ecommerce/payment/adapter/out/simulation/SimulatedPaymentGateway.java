package com.ecommerce.payment.adapter.out.simulation;

import com.ecommerce.payment.domain.port.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

/**
 * Simulated PSP. Two failure modes:
 *
 * <ul>
 *   <li><b>Deterministic:</b> any amount ending in .99 is declined with
 *       INSUFFICIENT_FUNDS — demos and integration tests can trigger a payment
 *       failure on purpose.</li>
 *   <li><b>Probabilistic:</b> everything else succeeds with the configured
 *       success rate; failures decline as CARD_DECLINED.</li>
 * </ul>
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PaymentSimulationProperties properties;
    private final RandomGenerator random;

    public SimulatedPaymentGateway(PaymentSimulationProperties properties, RandomGenerator random) {
        this.properties = properties;
        this.random = random;
    }

    @Override
    public ChargeResult charge(String orderId, BigDecimal amount, String currency) {
        simulateLatency();
        if (endsIn99(amount)) {
            log.info("Simulated decline (deterministic .99 rule) for order {}", orderId);
            return ChargeResult.declined("INSUFFICIENT_FUNDS");
        }
        if (random.nextDouble() >= properties.successRate()) {
            log.info("Simulated decline (random, success-rate={}) for order {}",
                    properties.successRate(), orderId);
            return ChargeResult.declined("CARD_DECLINED");
        }
        return ChargeResult.ok();
    }

    private boolean endsIn99(BigDecimal amount) {
        return amount.movePointRight(2).toBigInteger()
                .mod(HUNDRED.toBigInteger()).intValue() == 99;
    }

    private void simulateLatency() {
        if (properties.latencyMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.latencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
