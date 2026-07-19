package com.ecommerce.order.application.service;

import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryService {

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    public Order getById(String orderId) {
        return getById(orderId, null);
    }

    /**
     * @param ownerId when non-null, restricts the lookup to orders owned by
     *                this customer — a non-owner gets the same 404 as a
     *                genuinely missing order, never a 403, so existence isn't
     *                leaked to callers who don't own it.
     */
    public Order getById(String orderId, String ownerId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (ownerId != null && !ownerId.equals(order.getCustomerId())) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    public List<Order> list(String customerId) {
        return customerId == null ? orders.findAll() : orders.findByCustomerId(customerId);
    }
}
