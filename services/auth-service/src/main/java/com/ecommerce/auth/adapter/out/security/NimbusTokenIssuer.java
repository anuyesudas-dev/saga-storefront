package com.ecommerce.auth.adapter.out.security;

import com.ecommerce.auth.application.port.TokenIssuer;
import com.ecommerce.auth.domain.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * RS256: only this service holds the private key; verifiers (the gateway)
 * fetch the public half from /.well-known/jwks.json. The kid header lets
 * verifiers pick the right key across rotations.
 */
@Component
public class NimbusTokenIssuer implements TokenIssuer {

    public static final String ISSUER = "ecommerce-auth";

    private final RSAKey signingKey;
    private final Clock clock;
    private final AuthTokenProperties properties;

    public NimbusTokenIssuer(RSAKey signingKey, Clock clock, AuthTokenProperties properties) {
        this.signingKey = signingKey;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(user.getId())
                .claim("email", user.getEmail())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(signingKey.getKeyID())
                        .build(),
                claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign JWT", e);
        }
        return new IssuedToken(jwt.serialize(), expiresAt);
    }
}
