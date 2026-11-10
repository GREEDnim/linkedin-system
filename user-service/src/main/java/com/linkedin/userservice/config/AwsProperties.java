package com.linkedin.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the "aws" block of application.yaml.
 *
 * <p>{@code endpoint} is optional on purpose: when it is set (for example to a local
 * MinIO at http://localhost:9000) the SDK talks to that host instead of AWS. It exists
 * so the upload path can be exercised for real in development rather than stubbed.
 */
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String accessKey,
        String secretKey,
        String region,
        String endpoint,
        S3 s3) {

    public record S3(String bucketName) {
    }
}
