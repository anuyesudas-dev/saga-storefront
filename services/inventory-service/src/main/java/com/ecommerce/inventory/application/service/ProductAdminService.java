package com.ecommerce.inventory.application.service;

import com.ecommerce.inventory.domain.exception.ProductAlreadyExistsException;
import com.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.port.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Stock seeding/inspection for testing and demos — a real system would feed
 * stock from a supplier pipeline, not an admin API.
 */
@Service
public class ProductAdminService {

    private final ProductRepository products;
    private final Clock clock;

    public ProductAdminService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    public Product create(String productId, String name, int initialStock) {
        if (products.existsById(productId)) {
            throw new ProductAlreadyExistsException(productId);
        }
        return products.save(Product.create(productId, name, initialStock, clock.instant()));
    }

    public Product replenish(String productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.replenish(quantity, clock.instant());
        return products.save(product);
    }

    public Product get(String productId) {
        return products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public List<Product> list() {
        return products.findAll();
    }
}
