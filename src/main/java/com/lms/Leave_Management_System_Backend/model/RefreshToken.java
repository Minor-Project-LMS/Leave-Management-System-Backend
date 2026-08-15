package com.lms.Leave_Management_System_Backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;

/**
 * Redis entity for storing refresh tokens with automatic TTL expiration.
 * Enhanced for Refresh Token Rotation (RTR) and breach detection.
 * Stores SHA-256 hash of the actual token for security.
 */
@RedisHash("refresh_tokens")
public class RefreshToken {

    @Id
    private String tokenValue; // SHA-256 hash of the actual token

    @Indexed
    private String userId;

    private Long expirationTime;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long ttl;

    private String sessionFingerprint; // User-Agent hash for session binding
    private String sessionId; // Session family identifier for breach detection
    private boolean isRevoked; // Flag for replay detection
    private Long rotationCount; // Track rotation attempts for anomaly detection

    public RefreshToken() {
    }

    public RefreshToken(String tokenValue, String userId, long expirationTime, long ttl, 
                       String sessionFingerprint, String sessionId) {
        this.tokenValue = tokenValue;
        this.userId = userId;
        this.expirationTime = expirationTime;
        this.ttl = ttl;
        this.sessionFingerprint = sessionFingerprint;
        this.sessionId = sessionId;
        this.isRevoked = false;
        this.rotationCount = 0L;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    public String getSessionFingerprint() {
        return sessionFingerprint;
    }

    public void setSessionFingerprint(String sessionFingerprint) {
        this.sessionFingerprint = sessionFingerprint;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isRevoked() {
        return isRevoked;
    }

    public void setRevoked(boolean revoked) {
        isRevoked = revoked;
    }

    public Long getRotationCount() {
        return rotationCount;
    }

    public void setRotationCount(Long rotationCount) {
        this.rotationCount = rotationCount;
    }
}
