package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.config.AwsS3Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3BlobStorageService implements BlobStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3BlobStorageService.class);
    
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Config awsConfig;

    public S3BlobStorageService(S3Client s3Client, S3Presigner s3Presigner, AwsS3Config awsConfig) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.awsConfig = awsConfig;
    }

    @Override
    public URL generatePresignedPutUrl(String key, String contentType, Duration expiry) {
        String bucket = determineBucketFromKey(key);
        
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(b -> b.bucket(bucket).key(key).contentType(contentType))
                .build();
        
        return s3Presigner.presignPutObject(presignRequest).url();
    }

    @Override
    public URL generatePresignedGetUrl(String key, Duration expiry) {
        String bucket = determineBucketFromKey(key);
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(b -> b.bucket(bucket).key(key))
                .build();
        
        return s3Presigner.presignGetObject(presignRequest).url();
    }

    @Override
    public ObjectMetadata headObject(String key) throws Exception {
        String bucket = determineBucketFromKey(key);
        
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            HeadObjectResponse response = s3Client.headObject(headRequest);
            
            return new ObjectMetadata(
                    response.contentLength(),
                    response.contentType(),
                    response.eTag()
            );
        } catch (NoSuchKeyException e) {
            throw new Exception("Object not found: " + key, e);
        } catch (Exception e) {
            log.error("Failed to head object: {}", key, e);
            throw new Exception("Failed to check object existence: " + key, e);
        }
    }

    @Override
    public void putObject(String key, InputStream inputStream, String contentType, long contentLength) throws Exception {
        String bucket = determineBucketFromKey(key);
        
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            
            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, contentLength));
            
            log.info("Successfully uploaded object to S3: bucket={}, key={}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to upload object to S3: bucket={}, key={}", bucket, key, e);
            throw new Exception("Failed to upload object to S3: " + key, e);
        }
    }

    @Override
    public void deleteObject(String key) throws Exception {
        String bucket = determineBucketFromKey(key);
        
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteRequest);
            
            log.info("Successfully deleted object from S3: bucket={}, key={}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to delete object from S3: bucket={}, key={}", bucket, key, e);
            throw new Exception("Failed to delete object from S3: " + key, e);
        }
    }

    @Override
    public String getBucketForEntityType(String entityType) {
        if ("USER_AVATAR".equals(entityType)) {
            return awsConfig.getS3AvatarsBucket();
        }
        return awsConfig.getS3AttachmentsBucket();
    }

    @Override
    public String generateStorageKey(String entityType, Long entityId, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        if (entityId != null) {
            return String.format("%s/%d/%s_%s", entityType.toLowerCase(), entityId, uuid, sanitizedFileName);
        } else {
            return String.format("%s/temp/%s_%s", entityType.toLowerCase(), uuid, sanitizedFileName);
        }
    }

    private String determineBucketFromKey(String key) {
        // Simple heuristic: if key starts with "user_avatar/" use avatars bucket
        if (key.startsWith("user_avatar/")) {
            return awsConfig.getS3AvatarsBucket();
        }
        return awsConfig.getS3AttachmentsBucket();
    }
}
