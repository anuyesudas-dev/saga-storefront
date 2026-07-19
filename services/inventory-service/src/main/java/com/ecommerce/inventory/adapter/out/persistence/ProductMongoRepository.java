package com.ecommerce.inventory.adapter.out.persistence;

import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.port.ProductRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductMongoRepository extends MongoRepository<Product, String>, ProductRepository {
}
