package com.ecommerce.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Inventory Service API")
                .description("""
                        Stock administration and reservation inspection. The reservation \
                        flow itself is event-driven: OrderCreated reserves, payment \
                        outcome confirms or releases.""")
                .version("v1"));
    }
}
