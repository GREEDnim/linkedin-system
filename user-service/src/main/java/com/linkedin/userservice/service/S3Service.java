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
     * Uploads a file.
     *
     * @param file the multipart upload
     * @param keyPrefix object key prefix, e.g. "profiles/{userId}/avatar"
     * @return the stored key (for deleting or replacing the object) and the URL clients use
     */
    public StoredObject uploadFile(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File must not be empty");
        }

        // Quick sanity check on the client-declared content type: keeps .exe/.html
        // "images" out of the bucket, but it is not a security boundary — a real
        // one verifies magic bytes (e.g. Apache Tika).
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("Only image files are accepted");
        }

        String key = keyPrefix + "-" + UUID.randomUUID() + extractExtension(file.getOriginalFilename());

        String bucketName = awsProperties.s3().bucketName();

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentLength(file.getSize())
                .contentType(contentType);

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
        return new StoredObject(key, url);
    }

    /**
     * Removes an object. Best-effort: the caller is deleting or replacing a record it owns, and
     * failing that because storage hiccuped would be worse than leaving bytes behind. S3 treats
     * deleting an absent key as success, so this is safe to call for a stale key.
     */
    public void deleteObject(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(builder -> builder
                    .bucket(awsProperties.s3().bucketName())
                    .key(key));
            log.info("Deleted object {}", key);
        } catch (SdkException ex) {
            log.warn("Could not delete object {} - it is now orphaned: {}", key, ex.getMessage());
        }
    }

    /** @param key the object key, needed to delete it later; @param url what clients link to */
    public record StoredObject(String key, String url) {
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


