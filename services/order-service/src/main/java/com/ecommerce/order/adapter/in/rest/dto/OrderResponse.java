package com.ecommerce.order.adapter.in.rest.dto;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        List<Item> items,
        BigDecimal totalAmount,
        String currency,
        OrderStatus status,
        String statusReason,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(String productId, String productName, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.productId(), i.productName(), i.quantity(), i.unitPrice().amount()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), items,
                order.getTotalAmount().amount(), order.getTotalAmount().currency(),
                order.getStatus(), order.getStatusReason(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
