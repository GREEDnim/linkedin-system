package com.linkedin.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the "jwt" block of application.yaml.
 * Expirations are in milliseconds: they are added to System.currentTimeMillis().
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long expiration,
        long refreshExpiration) {
}
