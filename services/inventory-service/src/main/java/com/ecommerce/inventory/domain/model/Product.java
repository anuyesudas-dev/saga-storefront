package com.ecommerce.inventory.domain.model;

import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Stock aggregate. The invariant that must never break: {@code availableQty >= 0}
 * — no overselling. The @Version field is the enforcement mechanism under
 * concurrency: two orders racing for the last unit means one save loses on
 * version conflict, retries, sees insufficient stock, and fails cleanly.
 */
@Document(collection = "products")
public class Product {

    @Id
    private final String id;
    private final String name;
    /** Catalog display/checkout price — lives with stock rather than a
     * separate catalog-service, which would be overkill at this size. */
    private final BigDecimal price;
    private int availableQty;
    /** Units held by ACTIVE reservations — not sellable, not yet permanently gone. */
    private int reservedQty;
    @Version
    private Long version;
    private Instant updatedAt;

    @PersistenceCreator
    Product(String id, String name, BigDecimal price, int availableQty, int reservedQty, Long version, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public static Product create(String id, String name, BigDecimal price, int initialStock, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        if (initialStock < 0) {
            throw new IllegalArgumentException("initialStock must not be negative");
        }
        return new Product(id, name, price, initialStock, 0, null, now);
    }

    /** Phase 1 of the two-phase reservation: move stock from available to held. */
    public void reserve(int quantity, Instant now) {
        requirePositive(quantity);
        if (availableQty < quantity) {
            throw new InsufficientStockException(List.of(id));
        }
        availableQty -= quantity;
        reservedQty += quantity;
        updatedAt = now;
    }

    /** Phase 2a — payment succeeded: the hold becomes a permanent deduction. */
    public void confirmReservation(int quantity, Instant now) {
        requireHeld(quantity);
        reservedQty -= quantity;
        updatedAt = now;
    }

    /** Phase 2b — payment failed or order cancelled: the hold returns to stock. */
    public void releaseReservation(int quantity, Instant now) {
        requireHeld(quantity);
        reservedQty -= quantity;
        availableQty += quantity;
        updatedAt = now;
    }

    public void replenish(int quantity, Instant now) {
        requirePositive(quantity);
        availableQty += quantity;
        updatedAt = now;
    }

    public boolean hasAvailable(int quantity) {
        return availableQty >= quantity;
    }

    private void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, got: " + quantity);
        }
    }

    private void requireHeld(int quantity) {
        requirePositive(quantity);
        if (reservedQty < quantity) {
            throw new IllegalStateException(
                    "product %s has only %d unit(s) reserved, cannot settle %d"
                            .formatted(id, reservedQty, quantity));
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getAvailableQty() {
        return availableQty;
    }

    public int getReservedQty() {
        return reservedQty;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
