package com.linkedin.feedservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the "feed" block of application.yaml.
 */
@ConfigurationProperties(prefix = "feed")
public record FeedProperties(int maxFeedSize) {
}
