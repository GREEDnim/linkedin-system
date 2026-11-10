package com.linkedin.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The one value this gateway needs from user-service's JWT setup: the shared HS256
 * signing secret.
 *
 * <p>Only the secret is bound on purpose. Expiry is enforced by the token's own
 * {@code exp} claim, and claim names are constants in JwtVerifier - nothing else
 * about the token shape belongs in configuration.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {
}
