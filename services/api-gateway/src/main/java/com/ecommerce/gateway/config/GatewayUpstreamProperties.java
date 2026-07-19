package com.ecommerce.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("gateway.upstreams")
public record GatewayUpstreamProperties(
        @NotBlank String authService,
        @NotBlank String inventoryService,
        @NotBlank String orderService) {
}
