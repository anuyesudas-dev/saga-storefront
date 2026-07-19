package com.ecommerce.order.domain.model;

import com.ecommerce.order.domain.exception.IllegalOrderStateException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root for the order lifecycle. All status transitions go through the
 * methods below — the aggregate enforces the state machine, callers never set
 * status directly.
 *
 * <pre>
 * CREATED ──reserve──► INVENTORY_RESERVED ──pay──► PAID
 *    │                        │
 *    │                        ├──payment failed──► FAILED
 *    ├──inventory failed────► FAILED
 *    └──cancel (also from INVENTORY_RESERVED)────► CANCELLED
 * </pre>
 */
@Document(collection = "orders")
public class Order {

    @Id
    private final String id;
    private final String customerId;
    private final List<OrderItem> items;
    private final Money totalAmount;
    private OrderStatus status;
    /** Why the order ended in FAILED or CANCELLED; null otherwise. */
    private String statusReason;
    @Version
    private Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    @PersistenceCreator
    Order(String id, String customerId, List<OrderItem> items, Money totalAmount,
          OrderStatus status, String statusReason, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.status = status;
        this.statusReason = statusReason;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Order create(String id, String customerId, List<OrderItem> items, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("an order must contain at least one item");
        }
        Money total = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(Money::add)
                .orElseThrow();
        return new Order(id, customerId, List.copyOf(items), total,
                OrderStatus.CREATED, null, null, now, now);
    }

    public void markInventoryReserved(Instant now) {
        transitionTo(OrderStatus.INVENTORY_RESERVED, Set.of(OrderStatus.CREATED), null, now);
    }

    public void markPaid(Instant now) {
        transitionTo(OrderStatus.PAID, Set.of(OrderStatus.INVENTORY_RESERVED), null, now);
    }

    public void markFailed(String reason, Instant now) {
        transitionTo(OrderStatus.FAILED,
                Set.of(OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVED), reason, now);
    }

    /** Cancellation is only legal before payment completes (see ADR-0001 scope note). */
    public void cancel(String reason, Instant now) {
        transitionTo(OrderStatus.CANCELLED,
                Set.of(OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVED), reason, now);
    }

    private void transitionTo(OrderStatus target, Set<OrderStatus> allowedFrom,
                              String reason, Instant now) {
        if (!allowedFrom.contains(status)) {
            throw new IllegalOrderStateException(id, status, target);
        }
        this.status = target;
        this.statusReason = reason;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
