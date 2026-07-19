package com.ecommerce.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Auth Service API")
                .description("""
                        Registration and login. Login issues a short-lived RS256 JWT; \
                        verifiers fetch the public key from /.well-known/jwks.json. \
                        No refresh tokens — expiry means logging in again.""")
                .version("v1"));
    }
}
