package com.ecommerce.common.events.inventory;

import java.util.List;

public record InventoryReservationFailedPayload(
        String orderId,
        String customerId,
        String reason,
        List<String> unavailableProductIds) {
}
