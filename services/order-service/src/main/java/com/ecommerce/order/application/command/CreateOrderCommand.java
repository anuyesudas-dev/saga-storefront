package com.ecommerce.order.application.command;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(String customerId, String currency, List<NewOrderItem> items) {

    public record NewOrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
    }
}
