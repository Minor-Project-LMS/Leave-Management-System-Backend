package com.lms.Leave_Management_System_Backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final long MAX_FILE_SIZE_BYTES = 10_485_760; // 10 MB

    @Value("${aws.s3.endpoint:}")
    private String storageEndpoint;

    @Value("${aws.s3.attachments.bucket:lms-attachments}")
    private String bucketName;

    @Value("${aws.access-key:}")
    private String accessKey;

    @Value("${aws.secret-key:}")
    private String secretKey;

    @Value("${aws.region:us-east-1}")
    private String region;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public StorageService() {
        // Initialize will be called by Spring after properties are set
    }

    @jakarta.annotation.PostConstruct
    public void initialize() {
        try {
            if (storageEndpoint != null && !storageEndpoint.isEmpty() &&
                accessKey != null && !accessKey.isEmpty() &&
                secretKey != null && !secretKey.isEmpty()) {

                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

                var s3ClientBuilder = S3Client.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .region(Region.of(region));

                // Configure for custom endpoint (e.g., Supabase)
                if (!storageEndpoint.isEmpty()) {
                    s3ClientBuilder.endpointOverride(URI.create(storageEndpoint));
                    s3ClientBuilder.serviceConfiguration(b -> 
                        b.pathStyleAccessEnabled(true) // Required for Supabase
                    );
                }

                this.s3Client = s3ClientBuilder.build();

                // Initialize presigner
                S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .region(Region.of(region));

                if (!storageEndpoint.isEmpty()) {
                    presignerBuilder.endpointOverride(URI.create(storageEndpoint));
                }

                this.s3Presigner = presignerBuilder.build();

                log.info("Storage service initialized successfully with endpoint: {}", storageEndpoint);
            } else {
                log.warn("Storage service not initialized - missing configuration. File uploads will be disabled.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize storage service", e);
        }
    }

    /**
     * Generate a pre-signed PUT URL for direct-to-storage upload
     */
    public PresignedUploadResult generatePresignedPutUrl(String storageKey, String contentType, long sizeBytes) {
        if (s3Client == null || s3Presigner == null) {
            throw new IllegalStateException("Storage service is not properly configured");
        }

        // Validate file size
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 10 MB");
        }

        // Validate content type for images (for avatar uploads)
        if (contentType != null && contentType.startsWith("image/")) {
            // Allow common image types
            if (!contentType.equals("image/jpeg") && 
                !contentType.equals("image/png") && 
                !contentType.equals("image/gif") && 
                !contentType.equals("image/webp")) {
                throw new IllegalArgumentException("Unsupported image content type: " + contentType);
            }
        }

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(contentType)
                    .contentLength(sizeBytes)
                    .build();

            software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignedRequest = 
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5)) // 5 minute expiry
                    .putObjectRequest(putObjectRequest)
                    .build();

            var presignedUrl = s3Presigner.presignPutObject(presignedRequest);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", contentType);

            return new PresignedUploadResult(
                    presignedUrl.url().toString(),
                    headers,
                    300 // 5 minutes in seconds
            );

        } catch (Exception e) {
            log.error("Failed to generate presigned PUT URL for key: {}", storageKey, e);
            throw new RuntimeException("Failed to generate upload URL", e);
        }
    }

    /**
     * Generate a pre-signed GET URL for downloading
     */
    public String generatePresignedGetUrl(String storageKey) {
        if (s3Client == null || s3Presigner == null) {
            throw new IllegalStateException("Storage service is not properly configured");
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignedRequest = 
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15)) // 15 minute expiry for downloads
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presignedUrl = s3Presigner.presignGetObject(presignedRequest);
            return presignedUrl.url().toString();

        } catch (Exception e) {
            log.error("Failed to generate presigned GET URL for key: {}", storageKey, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    /**
     * Verify that an object exists in storage (HEAD request)
     */
    public boolean verifyObjectExists(String storageKey) {
        if (s3Client == null) {
            throw new IllegalStateException("Storage service is not properly configured");
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);
            return response != null;

        } catch (NoSuchKeyException e) {
            log.warn("Object not found in storage: {}", storageKey);
            return false;
        } catch (Exception e) {
            log.error("Failed to verify object existence for key: {}", storageKey, e);
            return false;
        }
    }

    /**
     * Get object metadata including size and ETag (for checksum verification)
     */
    public ObjectMetadata getObjectMetadata(String storageKey) {
        if (s3Client == null) {
            throw new IllegalStateException("Storage service is not properly configured");
        }

        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);
            
            return new ObjectMetadata(
                    response.contentLength(),
                    response.eTag()
            );

        } catch (NoSuchKeyException e) {
            log.warn("Object not found in storage: {}", storageKey);
            return null;
        } catch (Exception e) {
            log.error("Failed to get object metadata for key: {}", storageKey, e);
            return null;
        }
    }

    /**
     * Delete an object from storage
     */
    public boolean deleteObject(String storageKey) {
        if (s3Client == null) {
            throw new IllegalStateException("Storage service is not properly configured");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Successfully deleted object from storage: {}", storageKey);
            return true;

        } catch (Exception e) {
            log.error("Failed to delete object from storage: {}", storageKey, e);
            return false;
        }
    }

    /**
     * Check if storage service is properly configured
     */
    public boolean isConfigured() {
        return s3Client != null && s3Presigner != null;
    }

    // Inner classes for return types

    public static class PresignedUploadResult {
        private final String uploadUrl;
        private final Map<String, String> requiredHeaders;
        private final int expiresInSeconds;

        public PresignedUploadResult(String uploadUrl, Map<String, String> requiredHeaders, int expiresInSeconds) {
            this.uploadUrl = uploadUrl;
            this.requiredHeaders = requiredHeaders;
            this.expiresInSeconds = expiresInSeconds;
        }

        public String getUploadUrl() {
            return uploadUrl;
        }

        public Map<String, String> getRequiredHeaders() {
            return requiredHeaders;
        }

        public int getExpiresInSeconds() {
            return expiresInSeconds;
        }
    }

    public static class ObjectMetadata {
        private final long sizeBytes;
        private final String eTag;

        public ObjectMetadata(long sizeBytes, String eTag) {
            this.sizeBytes = sizeBytes;
            this.eTag = eTag;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getETag() {
            return eTag;
        }
    }
}
