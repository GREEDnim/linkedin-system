package com.linkedin.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the "jwt" block of application.yaml.
 *
 * <p>Expirations are in milliseconds: they are added to System.currentTimeMillis(). A
 * value copied from documentation that uses seconds yields tokens that expired before
 * they were issued, so JwtService rejects an invalid combination at startup rather than
 * failing per request.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long expiration,
        long refreshExpiration,
        String issuer) {
}
