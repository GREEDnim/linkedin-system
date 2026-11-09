package com.linkedin.userservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linkedin.userservice.config.AwsProperties;
import com.linkedin.userservice.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    /**
     * Uploads a file and returns its public URL.
     *
     * @param file the multipart upload
     * @param keyPrefix object key prefix, e.g. "profiles/{userId}/avatar"
     */
    public String uploadFile(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File must not be empty");
        }

        String key = keyPrefix + "-" + UUID.randomUUID() + extractExtension(file.getOriginalFilename());

        String bucketName = awsProperties.s3().bucketName();

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentLength(file.getSize());

        if (file.getContentType() != null) {
            request.contentType(file.getContentType());
        }

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request.build(),
                    RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException ex) {
            log.error("Failed to read uploaded file", ex);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read uploaded file", ex);
        } catch (SdkException ex) {
            // Covers S3Exception (service error) and SdkClientException (network/credentials).
            log.error("S3 upload failed for key {}", key, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "File upload failed", ex);
        }

        String url = s3Client.utilities()
                .getUrl(builder -> builder.bucket(bucketName).key(key))
                .toString();

        log.info("Uploaded file to {}", key);
        return url;
    }

    /** Keeps the original extension (".png"), so browsers serve the right content type. */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex);
    }
}


