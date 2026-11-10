package com.linkedin.apigateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins allowed to call the API.
 *
 * <p>An explicit list rather than a wildcard: the API authenticates with a bearer token in a
 * header, and a wildcard origin plus credentials is refused by browsers anyway. Origins are
 * exact - a wildcard subdomain would let any tenant of a shared host call the API.
 */
@ConfigurationProperties(prefix = "gateway.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
