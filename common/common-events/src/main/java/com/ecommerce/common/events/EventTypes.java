package com.ecommerce.common.events;

/** Logical event type names carried in {@link EventEnvelope#eventType()}. */
public final class EventTypes {

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed";
    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String NOTIFICATION_SENT = "NotificationSent";

    private EventTypes() {
    }
}
