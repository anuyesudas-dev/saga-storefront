package com.ecommerce.auth.adapter.out.security;

import com.ecommerce.auth.application.port.TokenIssuer.IssuedToken;
import com.ecommerce.auth.domain.model.User;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class NimbusTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    private static RSAKey key;

    @BeforeAll
    static void generateKey() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-kid").keyUse(KeyUse.SIGNATURE).generate();
    }

    @Test
    void issuesVerifiableTokenWithExpectedClaims() throws Exception {
        NimbusTokenIssuer issuer = new NimbusTokenIssuer(key,
                Clock.fixed(NOW, ZoneOffset.UTC), new AuthTokenProperties(Duration.ofMinutes(30)));
        User user = User.register("user-1", "anu@example.com", "hash", "Anu", NOW);

        IssuedToken issued = issuer.issue(user);

        SignedJWT jwt = SignedJWT.parse(issued.token());
        // verifiable with the PUBLIC half only — what the gateway will do via JWKS
        assertThat(jwt.verify(new RSASSAVerifier(key.toPublicJWK()))).isTrue();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("test-kid");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(NimbusTokenIssuer.ISSUER);
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.getStringClaim("email")).isEqualTo("anu@example.com");
        assertThat(claims.getIssueTime()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpirationTime()).isEqualTo(Date.from(NOW.plus(Duration.ofMinutes(30))));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void tamperedTokenFailsVerification() throws Exception {
        NimbusTokenIssuer issuer = new NimbusTokenIssuer(key,
                Clock.fixed(NOW, ZoneOffset.UTC), new AuthTokenProperties(Duration.ofMinutes(30)));
        User user = User.register("user-1", "anu@example.com", "hash", "Anu", NOW);

        String token = issuer.issue(user).token();
        String[] parts = token.split("\\.");
        // swap the payload for one claiming a different subject, keep the signature
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"someone-else\"}".getBytes());
        SignedJWT forged = SignedJWT.parse(parts[0] + "." + forgedPayload + "." + parts[2]);

        assertThat(forged.verify(new RSASSAVerifier(key.toPublicJWK()))).isFalse();
    }
}
