package com.ecommerce.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .description("""
                        Order lifecycle API — the saga entry point of the event-driven \
                        e-commerce platform. Creating an order publishes OrderCreated \
                        (via transactional outbox) and downstream services drive the \
                        order through its state machine.""")
                .version("v1"));
    }
}
