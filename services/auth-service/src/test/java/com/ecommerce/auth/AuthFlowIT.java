package com.ecommerce.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole contract over real HTTP + real Mongo: register, log in, and verify
 * the issued JWT against the JWKS endpoint — exactly what the gateway will do.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerLoginAndVerifyTokenAgainstJwks() throws Exception {
        ResponseEntity<Map> registered = rest.postForEntity("/api/v1/auth/register",
                Map.of("email", "flow@example.com", "password", "s3cret-password", "displayName", "Flow"),
                Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = (String) registered.getBody().get("userId");

        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "flow@example.com", "password", "s3cret-password"),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) login.getBody().get("token");

        // verify the way an external party would: public key from the JWKS endpoint
        String jwksJson = rest.getForObject("/.well-known/jwks.json", String.class);
        SignedJWT jwt = SignedJWT.parse(token);
        RSAKey publicKey = (RSAKey) JWKSet.parse(jwksJson)
                .getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(publicKey.isPrivate()).isFalse(); // the endpoint must never leak the private half
        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.getStringClaim("email")).isEqualTo("flow@example.com");
        assertThat(claims.getExpirationTime()).isAfter(new Date());
    }

    @Test
    void duplicateRegistrationConflicts() {
        Map<String, String> body = Map.of(
                "email", "dupe@example.com", "password", "s3cret-password", "displayName", "Dupe");

        assertThat(rest.postForEntity("/api/v1/auth/register", body, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(rest.postForEntity("/api/v1/auth/register", body, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() {
        rest.postForEntity("/api/v1/auth/register",
                Map.of("email", "real@example.com", "password", "s3cret-password", "displayName", "Real"),
                Map.class);

        ResponseEntity<Map> wrongPassword = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "real@example.com", "password", "not-the-password"), Map.class);
        ResponseEntity<Map> unknownEmail = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "ghost@example.com", "password", "whatever"), Map.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // same status AND same body — no user enumeration
        assertThat(wrongPassword.getBody()).isEqualTo(unknownEmail.getBody());
    }
}
