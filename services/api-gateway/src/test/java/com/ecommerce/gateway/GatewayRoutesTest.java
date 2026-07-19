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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms each route (docs/edge-design.md's table) forwards to the
 * expected rewritten upstream path, catalog stays GET-only, and the
 * X-User-Id identity header is stripped/re-set correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTest {

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
    }

    // WireMockExtension resets all stub mappings before every test method, so
    // stubs must be (re-)registered per-test, not once in @BeforeAll.
    @BeforeEach
    void stubJwksAndUpstreams() throws Exception {
        wiremock.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(new ObjectMapper().writeValueAsString(TestJwtSupport.jwks(SIGNING_KEY)))));
        wiremock.stubFor(post(urlEqualTo("/api/v1/auth/register"))
                .willReturn(aResponse().withStatus(201)));
        wiremock.stubFor(post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse().withStatus(200)));
        wiremock.stubFor(get(urlEqualTo("/api/v1/products"))
                .willReturn(aResponse().withStatus(200).withBody("[]")));
        wiremock.stubFor(get(urlMatching("/api/v1/products/.*"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));
        wiremock.stubFor(post(urlEqualTo("/api/v1/orders"))
                .willReturn(aResponse().withStatus(201)));
        wiremock.stubFor(get(urlEqualTo("/api/v1/orders"))
                .willReturn(aResponse().withStatus(200).withBody("[]")));
        wiremock.stubFor(get(urlMatching("/api/v1/orders/[^/]+"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));
        wiremock.stubFor(post(urlMatching("/api/v1/orders/[^/]+/cancel"))
                .willReturn(aResponse().withStatus(200)));
    }

    @Test
    void registerRouteRewritesPath() {
        restTemplate.postForEntity("/api/auth/register", emptyJsonBody(), String.class);
        wiremock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/register")));
    }

    @Test
    void loginRouteRewritesPath() {
        restTemplate.postForEntity("/api/auth/login", emptyJsonBody(), String.class);
        wiremock.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login")));
    }

    @Test
    void catalogListRouteRewritesPath() {
        assertThat(restTemplate.getForEntity("/api/catalog", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        wiremock.verify(getRequestedFor(urlEqualTo("/api/v1/products")));
    }

    @Test
    void catalogItemRouteRewritesPath() {
        assertThat(restTemplate.getForEntity("/api/catalog/widget-1", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        wiremock.verify(getRequestedFor(urlEqualTo("/api/v1/products/widget-1")));
    }

    @Test
    void catalogRouteRejectsNonGetVerbs() {
        // authenticate so Security's blanket authenticated() rule doesn't mask
        // the thing under test: no route matches POST /api/catalog/**, so it
        // falls through to a local 404 and never reaches the inventory-service stub
        HttpHeaders headers = jsonHeaders(validToken("user-1"));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/catalog/widget-1", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        wiremock.verify(0, postRequestedFor(urlMatching("/api/v1/products/.*")));
    }

    @Test
    void createOrderRouteRewritesPath() {
        String token = validToken("user-1");
        HttpHeaders headers = jsonHeaders(token);
        restTemplate.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        wiremock.verify(postRequestedFor(urlEqualTo("/api/v1/orders")));
    }

    @Test
    void listOrdersRouteRewritesPath() {
        String token = validToken("user-1");
        HttpHeaders headers = jsonHeaders(token);
        restTemplate.exchange("/api/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        wiremock.verify(getRequestedFor(urlEqualTo("/api/v1/orders")));
    }

    @Test
    void getOrderRouteRewritesPath() {
        String token = validToken("user-1");
        HttpHeaders headers = jsonHeaders(token);
        restTemplate.exchange("/api/orders/order-1", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        wiremock.verify(getRequestedFor(urlEqualTo("/api/v1/orders/order-1")));
    }

    @Test
    void cancelOrderRouteRewritesPath() {
        String token = validToken("user-1");
        HttpHeaders headers = jsonHeaders(token);
        restTemplate.exchange("/api/orders/order-1/cancel", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        wiremock.verify(postRequestedFor(urlEqualTo("/api/v1/orders/order-1/cancel")));
    }

    @Test
    void clientSuppliedUserIdHeaderIsStrippedAndReplaced() {
        String token = validToken("user-1");
        HttpHeaders headers = jsonHeaders(token);
        headers.add("X-User-Id", "attacker");

        restTemplate.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        wiremock.verify(postRequestedFor(urlEqualTo("/api/v1/orders"))
                .withHeader("X-User-Id", equalTo("user-1")));
    }

    @Test
    void xUserIdAbsentOnPublicRoutesWithoutToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-User-Id", "attacker");

        restTemplate.exchange("/api/catalog", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        wiremock.verify(getRequestedFor(urlEqualTo("/api/v1/products"))
                .withHeader("X-User-Id", absent()));
    }

    private String validToken(String subject) {
        return TestJwtSupport.signedToken(SIGNING_KEY, "ecommerce-auth", subject,
                Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES));
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<String> emptyJsonBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>("{}", headers);
    }
}
