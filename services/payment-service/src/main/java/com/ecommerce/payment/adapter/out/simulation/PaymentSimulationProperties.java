package com.ecommerce.payment.adapter.out.simulation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated configuration — fail at startup, not mid-payment. */
@Validated
@ConfigurationProperties(prefix = "payment.simulation")
public record PaymentSimulationProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double successRate,
        @Min(0) long latencyMs) {
}
