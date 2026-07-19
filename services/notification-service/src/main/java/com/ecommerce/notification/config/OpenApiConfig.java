package com.ecommerce.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Notification Service API")
                .description("""
                        Notification history. Sending is event-driven: saga events \
                        trigger simulated email and SMS notifications, each recorded \
                        here exactly once per (order, type, channel).""")
                .version("v1"));
    }
}
