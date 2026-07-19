package com.ecommerce.auth.adapter.in.rest;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Standard JWKS location so verifiers need nothing but this service's base URL. */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey signingKey) {
        // public half only, serialized once — the private key never leaves the JVM
        this.jwks = new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
