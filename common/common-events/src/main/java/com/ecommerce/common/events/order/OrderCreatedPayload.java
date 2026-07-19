package com.ecommerce.common.events.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedPayload(
        String orderId,
        String customerId,
        List<OrderLine> items,
        BigDecimal totalAmount,
        String currency) {
}
