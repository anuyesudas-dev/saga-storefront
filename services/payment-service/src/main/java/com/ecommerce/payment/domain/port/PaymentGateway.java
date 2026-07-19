package com.ecommerce.payment.domain.port;

import java.math.BigDecimal;

/**
 * Port to whatever charges the customer. Today the only adapter is a simulator;
 * a real PSP integration (Stripe, Adyen, ...) would be a new adapter behind the
 * same port — the use case would not change.
 */
public interface PaymentGateway {

    ChargeResult charge(String orderId, BigDecimal amount, String currency);

    record ChargeResult(boolean success, String failureReason) {

        public static ChargeResult ok() {
            return new ChargeResult(true, null);
        }

        public static ChargeResult declined(String reason) {
            return new ChargeResult(false, reason);
        }
    }
}
