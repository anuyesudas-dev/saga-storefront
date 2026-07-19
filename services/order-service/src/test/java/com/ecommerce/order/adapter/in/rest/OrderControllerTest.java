package com.ecommerce.order.adapter.in.rest;

import com.ecommerce.order.application.service.CancelOrderUseCase;
import com.ecommerce.order.application.service.CreateOrderUseCase;
import com.ecommerce.order.application.service.OrderQueryService;
import com.ecommerce.order.domain.exception.IllegalOrderStateException;
import com.ecommerce.order.domain.exception.OrderNotFoundException;
import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.OrderItem;
import com.ecommerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateOrderUseCase createOrder;
    @MockitoBean
    private CancelOrderUseCase cancelOrder;
    @MockitoBean
    private OrderQueryService queries;

    private Order sampleOrder() {
        return Order.create("order-1", "cust-1",
                List.of(new OrderItem("p1", "Keyboard", 2, Money.of(new BigDecimal("89.99"), "USD"))),
                NOW);
    }

    @Test
    void createReturns201WithLocationAndCorrelationHeader() throws Exception {
        when(createOrder.handle(any())).thenReturn(sampleOrder());

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"cust-1","currency":"USD",
                                 "items":[{"productId":"p1","productName":"Keyboard",
                                           "quantity":2,"unitPrice":89.99}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/orders/order-1")))
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(jsonPath("$.id").value("order-1"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(179.98));
    }

    @Test
    void createEchoesProvidedCorrelationId() throws Exception {
        when(createOrder.handle(any())).thenReturn(sampleOrder());

        mvc.perform(post("/api/v1/orders")
                        .header("X-Correlation-Id", "my-corr-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"cust-1","currency":"USD",
                                 "items":[{"productId":"p1","productName":"Keyboard",
                                           "quantity":2,"unitPrice":89.99}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "my-corr-id"));
    }

    @Test
    void invalidRequestReturns400ProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"","currency":"USD",
                                 "items":[{"productId":"p1","productName":"Keyboard",
                                           "quantity":0,"unitPrice":89.99}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.customerId").exists())
                .andExpect(jsonPath("$.errors['items[0].quantity']").exists());
    }

    @Test
    void unknownOrderReturns404ProblemDetail() throws Exception {
        when(queries.getById("ghost")).thenThrow(new OrderNotFoundException("ghost"));

        mvc.perform(get("/api/v1/orders/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    void cancellingPaidOrderReturns409ProblemDetail() throws Exception {
        when(cancelOrder.handle(eq("order-1"), any())).thenThrow(
                new IllegalOrderStateException("order-1", OrderStatus.PAID, OrderStatus.CANCELLED));

        mvc.perform(post("/api/v1/orders/order-1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Illegal order state transition"))
                .andExpect(jsonPath("$.currentStatus").value("PAID"));
    }
}
