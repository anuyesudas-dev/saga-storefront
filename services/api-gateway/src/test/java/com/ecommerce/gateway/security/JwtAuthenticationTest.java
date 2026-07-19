package com.ecommerce.gateway.security;

import com.ecommerce.gateway.support.TestJwtSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 22 acceptance bar (docs/edge-design.md): route tests with mocked
 * JWKS — public passes, missing/expired/forged JWT -> 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtAuthenticationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final RSAKey SIGNING_KEY = TestJwtSupport.generateKey("test-kid");
    private static final RSAKey OTHER_KEY = TestJwtSupport.generateKey("other-kid");

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> wiremock.baseUrl() + "/.well-known/jwks.json");
        registry.add("gateway.upstreams.auth-service", wiremock::baseUrl);
        registry.add("gateway.upstreams.inventory-service", wiremock::baseUrl);
        registry.add("gateway.upstreams.order-service", wiremock::baseUrl);
        registry.add("gateway.jwt.issuer", () -> "ecommerce-auth");
    }

    // WireMockExtension resets all stub mappings before every test method, so
    // stubs must be (re-)registered per-test, not once in @BeforeAll.
    @BeforeEach
    void stubUpstreams() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new ObjectMapper().writeValueAsString(TestJwtSupport.jwks(SIGNING_KEY)))));
        wiremock.stubFor(get(urlEqualTo("/api/v1/orders"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody("[]")));
        wiremock.stubFor(get(urlEqualTo("/api/v1/products"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody("[]")));
    }

    @Test
    void missingTokenOnProtectedRouteReturns401() {
        assertThat(restTemplate.getForEntity("/api/orders", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenReturns401() {
        String token = TestJwtSupport.signedToken(SIGNING_KEY, "ecommerce-auth", "user-1",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().minus(30, ChronoUnit.MINUTES));
        assertThat(callOrders(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgedTokenReturns401() {
        // signed with a keypair never published in the stubbed JWKS
        String token = TestJwtSupport.signedToken(OTHER_KEY, "ecommerce-auth", "user-1",
                Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES));
        assertThat(callOrders(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongIssuerTokenReturns401() {
        String token = TestJwtSupport.signedToken(SIGNING_KEY, "some-other-issuer", "user-1",
                Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES));
        assertThat(callOrders(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenOnProtectedRouteReturns200() {
        String token = TestJwtSupport.signedToken(SIGNING_KEY, "ecommerce-auth", "user-1",
                Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES));
        assertThat(callOrders(token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicRoutesDoNotRequireAToken() {
        assertThat(restTemplate.getForEntity("/api/catalog", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> callOrders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
