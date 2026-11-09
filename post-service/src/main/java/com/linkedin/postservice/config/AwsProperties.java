package com.linkedin.postservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the "aws" block of application.yaml.
 */
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String accessKey,
        String secretKey,
        String region,
        S3 s3) {

    public record S3(String bucketName) {
    }
}
