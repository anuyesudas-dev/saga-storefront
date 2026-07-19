package com.ecommerce.order.adapter.out.persistence;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.OrderRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Zero-boilerplate adapter: extends both Spring Data's MongoRepository and the
 * domain's OrderRepository port — the method signatures line up, so Spring Data
 * generates the implementation and the domain port is satisfied for free.
 */
public interface OrderMongoRepository extends MongoRepository<Order, String>, OrderRepository {
}
