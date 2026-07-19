package com.ecommerce.inventory.domain.exception;

public class ProductAlreadyExistsException extends RuntimeException {

    public ProductAlreadyExistsException(String productId) {
        super("Product already exists: " + productId);
    }
}
