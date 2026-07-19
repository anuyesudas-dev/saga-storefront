package com.ecommerce.payment.adapter.in.rest;

import com.ecommerce.payment.adapter.in.rest.dto.PaymentResponse;
import com.ecommerce.payment.domain.port.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment inspection (processing is event-driven)")
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping
    @Operation(summary = "List payments, or fetch the payment for one order")
    public ResponseEntity<?> list(@RequestParam(required = false) String orderId) {
        if (orderId == null) {
            return ResponseEntity.ok(
                    payments.findAll().stream().map(PaymentResponse::from).toList());
        }
        return payments.findByOrderId(orderId)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(PaymentResponse.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }
}
