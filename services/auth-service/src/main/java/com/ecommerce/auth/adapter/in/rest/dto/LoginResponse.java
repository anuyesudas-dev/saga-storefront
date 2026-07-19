package com.ecommerce.auth.adapter.in.rest.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
}
