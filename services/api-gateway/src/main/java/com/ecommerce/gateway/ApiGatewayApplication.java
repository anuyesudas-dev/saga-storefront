package com.ecommerce.gateway;

import com.ecommerce.gateway.config.GatewayCorsProperties;
import com.ecommerce.gateway.config.GatewayJwtProperties;
import com.ecommerce.gateway.config.GatewayUpstreamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        GatewayUpstreamProperties.class,
        GatewayJwtProperties.class,
        GatewayCorsProperties.class
})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
