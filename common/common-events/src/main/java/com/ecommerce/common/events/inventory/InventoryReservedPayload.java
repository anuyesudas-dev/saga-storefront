package com.ecommerce.common.events.inventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Carries {@code totalAmount}/{@code currency} forward from the order even though
 * inventory does not use them: payment-service's only input is this event
 * (event-carried state transfer — see ADR-0004).
 */
public record InventoryReservedPayload(
        String orderId,
        String customerId,
        String reservationId,
        List<ReservedLine> items,
        BigDecimal totalAmount,
        String currency) {
}
