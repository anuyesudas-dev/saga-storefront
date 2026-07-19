package com.ecommerce.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static com.ecommerce.gateway.filter.IdentityHeaderFilterFunctions.propagateUserIdFromJwt;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * The gateway's full route table (docs/edge-design.md). GET-only definitions
 * for the catalog routes structurally block POST/PUT to /api/catalog/** (no
 * route matches, falls through to 404) — browsing needs no account, and that
 * holds even before Spring Security's authorization rules are consulted.
 */
@Configuration
public class RouteConfig {

    @Bean
    RouterFunction<ServerResponse> registerRoute(GatewayUpstreamProperties upstreams) {
        return route("auth-register")
                .POST("/api/auth/register", http())
                .before(uri(upstreams.authService()))
                .before(setPath("/api/v1/auth/register"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> loginRoute(GatewayUpstreamProperties upstreams) {
        return route("auth-login")
                .POST("/api/auth/login", http())
                .before(uri(upstreams.authService()))
                .before(setPath("/api/v1/auth/login"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> catalogListRoute(GatewayUpstreamProperties upstreams) {
        return route("catalog-list")
                .GET("/api/catalog", http())
                .before(uri(upstreams.inventoryService()))
                .before(setPath("/api/v1/products"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> catalogItemRoute(GatewayUpstreamProperties upstreams) {
        return route("catalog-item")
                .GET("/api/catalog/{*path}", http())
                .before(uri(upstreams.inventoryService()))
                // the {*path} predicate captures under variable name "path" (no asterisk)
                .before(setPath("/api/v1/products/{path}"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> createOrderRoute(GatewayUpstreamProperties upstreams) {
        return route("orders-create")
                .POST("/api/orders", http())
                .before(uri(upstreams.orderService()))
                .before(setPath("/api/v1/orders"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> listOrdersRoute(GatewayUpstreamProperties upstreams) {
        return route("orders-list")
                .GET("/api/orders", http())
                .before(uri(upstreams.orderService()))
                .before(setPath("/api/v1/orders"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> getOrderRoute(GatewayUpstreamProperties upstreams) {
        return route("orders-get")
                .GET("/api/orders/{id}", http())
                .before(uri(upstreams.orderService()))
                .before(setPath("/api/v1/orders/{id}"))
                .before(propagateUserIdFromJwt())
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> cancelOrderRoute(GatewayUpstreamProperties upstreams) {
        return route("orders-cancel")
                .POST("/api/orders/{id}/cancel", http())
                .before(uri(upstreams.orderService()))
                .before(setPath("/api/v1/orders/{id}/cancel"))
                .before(propagateUserIdFromJwt())
                .build();
    }
}
