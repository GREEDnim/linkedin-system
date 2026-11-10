package com.linkedin.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Request budgets enforced at the edge.
 *
 * <p>Two buckets, because the two kinds of endpoint fail differently under load. The auth
 * bucket is the one that matters: without it, {@code /auth/login} answers unlimited
 * password guesses, which is the single cheapest attack against any login form. Everything
 * else gets a much larger budget that exists to stop runaway clients rather than to shape
 * traffic.
 *
 * @param authRequestsPerMinute per client IP, for /api/v1/auth/**
 * @param apiRequestsPerMinute  per client IP, for every other protected route
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int authRequestsPerMinute,
        int apiRequestsPerMinute) {
}
