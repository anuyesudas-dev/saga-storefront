package com.ecommerce.inventory.adapter.in.rest.dto;

import com.ecommerce.inventory.domain.model.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            @NotBlank String productId,
            @NotBlank String name,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
            @Min(0) int initialStock) {
    }

    public record ReplenishRequest(@Min(1) int quantity) {
    }

    public record ProductResponse(
            String productId, String name, BigDecimal price, int availableQty, int reservedQty, Instant updatedAt) {

        public static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getPrice(),
                    product.getAvailableQty(), product.getReservedQty(), product.getUpdatedAt());
        }
    }
}
