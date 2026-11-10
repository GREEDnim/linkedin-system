package com.linkedin.postservice.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * S3Client is a library class: it cannot be annotated with @Component and needs
 * credentials + region to be built, so it is declared as a @Bean. It is a
 * singleton, which is what you want — it owns an HTTP connection pool.
 */
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

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
