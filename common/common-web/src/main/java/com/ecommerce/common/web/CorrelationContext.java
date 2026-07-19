package com.ecommerce.common.web;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Access to the current correlation id. Entry points populate MDC — the web
 * filter for REST, the Kafka listeners for events — and anything downstream
 * reads it from here so it never has to be threaded through method signatures.
 */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    /** Returns the current correlation id, minting one if the entry point set none. */
    public static String currentOrNew() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
