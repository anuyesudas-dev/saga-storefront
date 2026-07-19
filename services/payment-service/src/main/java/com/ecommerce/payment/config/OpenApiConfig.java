package com.ecommerce.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Service API")
                .description("""
                        Payment inspection. Charging is event-driven (InventoryReserved \
                        triggers it) and simulated: amounts ending in .99 always decline, \
                        everything else succeeds at the configured success rate.""")
                .version("v1"));
    }
}
