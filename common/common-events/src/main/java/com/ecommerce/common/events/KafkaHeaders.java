package com.ecommerce.common.events;

/** Custom Kafka record header names (duplicated from the envelope for tooling access). */
public final class KafkaHeaders {

    public static final String CORRELATION_ID = "x-correlation-id";
    public static final String EVENT_TYPE = "x-event-type";

    private KafkaHeaders() {
    }
}
