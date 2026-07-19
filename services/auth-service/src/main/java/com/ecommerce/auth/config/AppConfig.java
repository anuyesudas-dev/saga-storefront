package com.ecommerce.auth.config;

import com.ecommerce.auth.adapter.out.security.AuthTokenProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(AuthTokenProperties.class)
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Ephemeral signing key, generated at startup. Restart invalidates all
     * tokens — acceptable locally, and the JWKS + kid contract means verifiers
     * pick up the new key automatically. Loading a PEM from the platform
     * Secret is the multi-replica / keep-sessions evolution.
     */
    @Bean
    public RSAKey rsaSigningKey() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .generate();
    }
}
