package com.ecommerce.gateway;

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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS is handled only at the gateway (docs/edge-design.md): allowed
 * origins get the storefront's methods/headers, everyone else gets nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsConfigTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final RSAKey SIGNING_KEY = TestJwtSupport.generateKey("test-kid");

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
        registry.add("gateway.cors.allowed-origins[0]", () -> "http://localhost:5173");
    }

    // WireMockExtension resets all stub mappings before every test method, so
    // stubs must be (re-)registered per-test, not once in @BeforeAll.
    @BeforeEach
    void stubJwks() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(new ObjectMapper().writeValueAsString(TestJwtSupport.jwks(SIGNING_KEY)))));
    }

    @Test
    void allowedOriginGetsCorsHeadersOnPreflight() {
        ResponseEntity<Void> response = preflight("http://localhost:5173");

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(response.getHeaders().getAccessControlAllowMethods())
                .contains(HttpMethod.GET, HttpMethod.POST, HttpMethod.OPTIONS);
        assertThat(response.getHeaders().getAccessControlAllowHeaders())
                .contains("Authorization", "Content-Type");
    }

    @Test
    void unlistedOriginGetsNoCorsHeaders() {
        ResponseEntity<Void> response = preflight("http://evil.example.com");

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private ResponseEntity<Void> preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(java.util.List.of("Authorization", "Content-Type"));
        return restTemplate.exchange("/api/orders", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);
    }
}
