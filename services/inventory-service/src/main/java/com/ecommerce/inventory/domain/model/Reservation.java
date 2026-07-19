package com.ecommerce.inventory.domain.model;

import com.ecommerce.inventory.domain.exception.IllegalReservationStateException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A stock hold for one order, spanning its products. Lifecycle mirrors two-phase
 * commit: ACTIVE (hold placed) → CONFIRMED (payment ok, stock permanently gone)
 * or RELEASED (payment failed / order cancelled, stock returned).
 *
 * <p>The unique index on {@code orderId} is a second idempotency belt beyond the
 * processed-events ledger: a duplicate OrderCreated physically cannot create a
 * second hold.
 */
@Document(collection = "reservations")
public class Reservation {

    @Id
    private final String id;
    @Indexed(unique = true)
    private final String orderId;
    private final List<ReservedItem> items;
    private ReservationStatus status;
    @Version
    private Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    @PersistenceCreator
    Reservation(String id, String orderId, List<ReservedItem> items, ReservationStatus status,
                Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.items = items;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Reservation active(String id, String orderId, List<ReservedItem> items, Instant now) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("a reservation must hold at least one item");
        }
        return new Reservation(id, orderId, List.copyOf(items), ReservationStatus.ACTIVE, null, now, now);
    }

    /**
     * Placed when a release arrives for an order we never reserved (order-cancelled
     * overtook order-created across topics). The tombstone makes the later
     * OrderCreated a no-op instead of reserving stock for a dead order.
     */
    public static Reservation releasedTombstone(String id, String orderId, Instant now) {
        return new Reservation(id, orderId, List.of(), ReservationStatus.RELEASED, null, now, now);
    }

    public void confirm(Instant now) {
        transition(ReservationStatus.CONFIRMED, now);
    }

    public void release(Instant now) {
        transition(ReservationStatus.RELEASED, now);
    }

    private void transition(ReservationStatus target, Instant now) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalReservationStateException(orderId, status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public List<ReservedItem> getItems() {
        return items;
    }

    public ReservationStatus getStatus() {
        return status;
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
