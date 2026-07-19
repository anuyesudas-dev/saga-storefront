package com.ecommerce.auth.adapter.out.security;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("auth.token")
public record AuthTokenProperties(@NotNull Duration ttl) {
}
