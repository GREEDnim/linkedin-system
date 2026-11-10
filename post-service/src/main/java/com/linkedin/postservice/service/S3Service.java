package com.linkedin.postservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linkedin.postservice.config.AwsProperties;
import com.linkedin.postservice.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Object storage for post media.
 *
 * <p>Returns the object key alongside the URL, because the key is the only thing that can
 * delete the object later. It cannot be reconstructed from the post: keys are namespaced by
 * author, so a deleted post's row cannot be turned back into a key unless it was stored.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    /**
     * Uploads a file.
     *
     * @param keyPrefix object key prefix, e.g. "posts/{authorId}/image"
     * @return the stored key (for later deletion) and the URL clients should use
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
     * Removes an object. Deliberately best-effort and idempotent: the caller is deleting a row
     * it has every right to delete, and failing that because object storage hiccuped would be
     * worse than leaving bytes behind. S3 treats deleting an absent key as success anyway.
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
