package com.ecommerce.common.events.order;

import java.math.BigDecimal;

public record OrderLine(
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice) {
}
