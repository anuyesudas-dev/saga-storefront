package com.ecommerce.auth.application.port;

import com.ecommerce.auth.domain.model.User;

import java.time.Instant;

/** Port so the application layer never sees JOSE types — swapping the token tech touches one adapter. */
public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String token, Instant expiresAt) {
    }
}
