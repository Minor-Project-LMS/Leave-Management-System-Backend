package com.lms.Leave_Management_System_Backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConfigurationProperties(prefix = "aws")
public class AwsS3Config {
    
    private String region = "us-east-1";
    private String s3AttachmentsBucket = "lms-attachments";
    private String s3AvatarsBucket = "lms-avatars";
    private String s3Endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private int s3PresignedUrlExpiryMinutes = 15;

    // Getters and Setters
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getS3AttachmentsBucket() {
        return s3AttachmentsBucket;
    }

    public void setS3AttachmentsBucket(String s3AttachmentsBucket) {
        this.s3AttachmentsBucket = s3AttachmentsBucket;
    }

    public String getS3AvatarsBucket() {
        return s3AvatarsBucket;
    }

    public void setS3AvatarsBucket(String s3AvatarsBucket) {
        this.s3AvatarsBucket = s3AvatarsBucket;
    }

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getS3PresignedUrlExpiryMinutes() {
        return s3PresignedUrlExpiryMinutes;
    }

    public void setS3PresignedUrlExpiryMinutes(int s3PresignedUrlExpiryMinutes) {
        this.s3PresignedUrlExpiryMinutes = s3PresignedUrlExpiryMinutes;
    }
}
