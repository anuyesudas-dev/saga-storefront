package com.ecommerce.inventory.domain.port;

import com.ecommerce.inventory.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(String id);

    List<Product> findAll();

    boolean existsById(String id);
}
