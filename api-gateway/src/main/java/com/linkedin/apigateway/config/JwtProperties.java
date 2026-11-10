package com.linkedin.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What this gateway needs to verify a token: the shared HS256 secret and the issuer it
 * expects.
 *
 * <p>Only these two are bound on purpose. Expiry is enforced by the token's own {@code exp}
 * claim and the claim names are constants in JwtVerifier - nothing else about the token
 * shape belongs in configuration.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, String issuer) {
}
