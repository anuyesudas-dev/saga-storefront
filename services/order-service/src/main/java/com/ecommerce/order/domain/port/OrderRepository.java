package com.ecommerce.order.domain.port;

import com.ecommerce.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port owned by the domain. The Mongo adapter implements it;
 * the domain never sees Spring Data types beyond this contract.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);

    List<Order> findAll();
}
