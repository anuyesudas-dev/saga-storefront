package com.ecommerce.common.events;

/** Kafka topic names. Topics are created declaratively by their producer service. */
public final class Topics {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String INVENTORY_RESERVED = "inventory-reserved";
    public static final String INVENTORY_FAILED = "inventory-failed";
    public static final String PAYMENT_SUCCESS = "payment-success";
    public static final String PAYMENT_FAILED = "payment-failed";
    public static final String NOTIFICATION_SENT = "notification-sent";

    /** Suffix appended by the DeadLetterPublishingRecoverer (Spring Kafka default). */
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}
