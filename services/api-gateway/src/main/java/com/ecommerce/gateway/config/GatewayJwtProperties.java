package com.ecommerce.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("gateway.jwt")
public record GatewayJwtProperties(@NotBlank String issuer) {
}
