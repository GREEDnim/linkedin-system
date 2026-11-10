package com.linkedin.userservice.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@RequiredArgsConstructor
public class AWSConfig {

    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(awsProperties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                awsProperties.accessKey(),
                                awsProperties.secretKey())));

        if (awsProperties.endpoint() != null && !awsProperties.endpoint().isBlank()) {
            // MinIO (and any S3-compatible store) is addressed as host:port/bucket/key,
            // never the virtual-host style AWS defaults to - hence path style.
            builder.endpointOverride(URI.create(awsProperties.endpoint()))
                    .forcePathStyle(true);
        }

        return builder.build();
    }
}
