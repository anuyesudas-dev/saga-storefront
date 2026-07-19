package com.ecommerce.order.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addAndMultiply() {
        Money price = Money.of(new BigDecimal("89.99"), "USD");

        assertThat(price.multiply(2)).isEqualTo(Money.of(new BigDecimal("179.98"), "USD"));
        assertThat(price.add(Money.of(new BigDecimal("0.01"), "USD")))
                .isEqualTo(Money.of(new BigDecimal("90.00"), "USD"));
    }

    @Test
    void rejectsCurrencyMismatch() {
        assertThatThrownBy(() ->
                Money.of(BigDecimal.ONE, "USD").add(Money.of(BigDecimal.ONE, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismatch");
    }

    @Test
    void rejectsNegativeAmountAndBadCurrency() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "DOLLARS"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
