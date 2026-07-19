package com.ecommerce.payment.adapter.in.rest;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.port.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentRepository payments;

    @Test
    void returnsPaymentForOrder() throws Exception {
        when(payments.findByOrderId("order-1")).thenReturn(Optional.of(
                Payment.failed("pay-1", "order-1", "cust-1",
                        new BigDecimal("19.99"), "USD", "INSUFFICIENT_FUNDS", NOW)));

        mvc.perform(get("/api/v1/payments").param("orderId", "order-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        when(payments.findByOrderId("ghost")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/payments").param("orderId", "ghost"))
                .andExpect(status().isNotFound());
    }
}
