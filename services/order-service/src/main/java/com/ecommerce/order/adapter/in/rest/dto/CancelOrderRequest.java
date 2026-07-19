package com.ecommerce.order.adapter.in.rest.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Size(max = 500) String reason) {

    public String reasonOrDefault() {
        return reason == null || reason.isBlank() ? "customer request" : reason;
    }
}
