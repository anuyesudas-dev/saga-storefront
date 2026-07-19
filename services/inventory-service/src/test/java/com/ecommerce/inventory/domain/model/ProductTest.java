package com.ecommerce.inventory.domain.model;

import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Test
    void reserveMovesStockFromAvailableToHeld() {
        Product product = Product.create("p1", "Keyboard", 10, NOW);

        product.reserve(3, NOW);

        assertThat(product.getAvailableQty()).isEqualTo(7);
        assertThat(product.getReservedQty()).isEqualTo(3);
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        Product product = Product.create("p1", "Keyboard", 2, NOW);

        assertThatThrownBy(() -> product.reserve(3, NOW))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(product.getAvailableQty()).isEqualTo(2);
        assertThat(product.getReservedQty()).isZero();
    }

    @Test
    void confirmMakesDeductionPermanent() {
        Product product = Product.create("p1", "Keyboard", 10, NOW);
        product.reserve(3, NOW);

        product.confirmReservation(3, NOW);

        assertThat(product.getAvailableQty()).isEqualTo(7);
        assertThat(product.getReservedQty()).isZero();
    }

    @Test
    void releaseReturnsHeldStock() {
        Product product = Product.create("p1", "Keyboard", 10, NOW);
        product.reserve(3, NOW);

        product.releaseReservation(3, NOW);

        assertThat(product.getAvailableQty()).isEqualTo(10);
        assertThat(product.getReservedQty()).isZero();
    }

    @Test
    void cannotSettleMoreThanIsHeld() {
        Product product = Product.create("p1", "Keyboard", 10, NOW);
        product.reserve(1, NOW);

        assertThatThrownBy(() -> product.confirmReservation(2, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> product.releaseReservation(2, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void replenishAddsAvailableStock() {
        Product product = Product.create("p1", "Keyboard", 1, NOW);

        product.replenish(5, NOW);

        assertThat(product.getAvailableQty()).isEqualTo(6);
    }
}
