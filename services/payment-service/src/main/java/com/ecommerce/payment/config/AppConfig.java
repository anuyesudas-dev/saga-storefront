package com.ecommerce.payment.config;

import com.ecommerce.payment.adapter.out.simulation.PaymentSimulationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.random.RandomGenerator;

@Configuration
@EnableConfigurationProperties(PaymentSimulationProperties.class)
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Injected so tests can pin the simulator's randomness. Plain Random (java.base)
     * rather than RandomGenerator.getDefault(): the default algorithm lives in the
     * jdk.random module, which slim JRE container images do not ship.
     */
    @Bean
    public RandomGenerator randomGenerator() {
        return new java.util.Random();
    }
}
