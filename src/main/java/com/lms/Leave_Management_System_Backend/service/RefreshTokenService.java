package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.RefreshToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed refresh token service with Refresh Token Rotation (RTR) and breach detection.
 * Implements session fingerprinting, token rotation, and replay attack prevention.
 * Storage layout in Redis:
 *  - refresh_token:{hashedToken}          -> RefreshToken object (the token record itself)
 *  - refresh_token:user:{userId}          -> Set<hashedToken>  (index for revokeByUserId)
 *  - refresh_token:session:{sessionId}    -> Set<hashedToken>  (index for revokeAllTokensInSession)
 * The index sets let us avoid an expensive KEYS/SCAN over the whole keyspace when we
 * need to revoke "all tokens for this user" or "all tokens in this session family".
 */
@Service
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "refresh_token:";
    private static final String USER_INDEX_PREFIX = "refresh_token:user:";
    private static final String SESSION_INDEX_PREFIX = "refresh_token:session:";

    private final RedisTemplate<String, RefreshToken> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom;

    @Value("${jwt.refresh_expiration_seconds : 18000}")
    private long refreshExpirySeconds;

    @Autowired
    public RefreshTokenService(RedisTemplate<String, RefreshToken> redisTemplate) {
        // Use the injected, already-configured template instead of constructing a new,
        // disconnected one.
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = new StringRedisTemplate(Objects.requireNonNull(redisTemplate.getConnectionFactory()));
        this.secureRandom = new SecureRandom();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String generateSessionFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "unknown";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String generateSessionId() {
        byte[] randomByte = new byte[32];
        secureRandom.nextBytes(randomByte);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomByte);
    }

    // ---------- Redis helpers ----------

    private RefreshToken findByHashedToken(String hashedToken) {
        return redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + hashedToken);
    }

    /** Persist/overwrite a token record, re-deriving its TTL from expirationTime. */
    private void saveToken(String hashedToken, RefreshToken refreshToken) {
        long ttlSeconds = Math.max(1, (refreshToken.getExpirationTime() - System.currentTimeMillis()) / 1000);
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + hashedToken, refreshToken, Duration.ofSeconds(ttlSeconds));
    }

    /** Remove a token record and clean up its entries in the user/session index sets. */
    private void deleteToken(String hashedToken, RefreshToken refreshToken) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + hashedToken);
        if (refreshToken != null) {
            stringRedisTemplate.opsForSet().remove(USER_INDEX_PREFIX + refreshToken.getUserId(), hashedToken);
            if (refreshToken.getSessionId() != null) {
                stringRedisTemplate.opsForSet().remove(SESSION_INDEX_PREFIX + refreshToken.getSessionId(), hashedToken);
            }
        }
    }

    private void indexToken(String hashedToken, String normalizedUserId, String sessionId) {
        stringRedisTemplate.opsForSet().add(USER_INDEX_PREFIX + normalizedUserId, hashedToken);
        stringRedisTemplate.expire(USER_INDEX_PREFIX + normalizedUserId, Duration.ofSeconds(refreshExpirySeconds));
        stringRedisTemplate.opsForSet().add(SESSION_INDEX_PREFIX + sessionId, hashedToken);
        stringRedisTemplate.expire(SESSION_INDEX_PREFIX + sessionId, Duration.ofSeconds(refreshExpirySeconds));
    }

    // ---------- Public API ----------

    /**
     * Store a refresh token with session fingerprinting and session family tracking.
     */
    public void store(String token, String userId, HttpServletRequest request) {
        if (token == null || userId == null) {
            return;
        }

        String hashedToken = hashToken(token);
        String sessionFingerprint = generateSessionFingerprint(request);
        String sessionId = generateSessionId();
        String normalizedUserId = userId.toLowerCase();
        long expirationTime = System.currentTimeMillis() + (refreshExpirySeconds * 1000L);

        RefreshToken refreshToken = new RefreshToken(
                hashedToken,
                normalizedUserId,
                expirationTime,
                refreshExpirySeconds,
                sessionFingerprint,
                sessionId
        );

        saveToken(hashedToken, refreshToken);
        indexToken(hashedToken, normalizedUserId, sessionId);
    }

    /**
     * Validate token with fingerprint checking and replay detection.
     */
    public boolean validate(String token, String userId, HttpServletRequest request) {
        if (token == null || userId == null) {
            return false;
        }

        String hashedToken = hashToken(token);
        RefreshToken refreshToken = findByHashedToken(hashedToken);
        if (refreshToken == null) {
            return false;
        }

        // Check if token has been revoked (replay detection)
        if (refreshToken.isRevoked()) {
            return false;
        }

        // Check if token belongs to the correct user
        if (!refreshToken.getUserId().equalsIgnoreCase(userId.toLowerCase())) {
            return false;
        }

        // Check session fingerprint if available
        if (!"no-fingerprint".equals(refreshToken.getSessionFingerprint())) {
            String currentFingerprint = generateSessionFingerprint(request);
            if (!currentFingerprint.equals(refreshToken.getSessionFingerprint())) {
                // Fingerprint mismatch - potential session hijacking
                revokeAllTokensInSession(refreshToken.getSessionId());
                return false;
            }
        }

        // Check if token has expired
        long now = System.currentTimeMillis();
        if (now > refreshToken.getExpirationTime()) {
            deleteToken(hashedToken, refreshToken);
            return false;
        }

        return true;
    }

    /**
     * Revoke a specific token.
     */
    public void revoke(String token) {
        if (token == null) {
            return;
        }
        String hashedToken = hashToken(token);
        RefreshToken refreshToken = findByHashedToken(hashedToken);
        deleteToken(hashedToken, refreshToken);
    }

    /**
     * Revoke all tokens for a specific user.
     */
    public void revokeByUserId(String userId) {
        if (userId == null) {
            return;
        }
        String normalizedUserId = userId.toLowerCase();
        String userIndexKey = USER_INDEX_PREFIX + normalizedUserId;

        Set<String> hashedTokens = stringRedisTemplate.opsForSet().members(userIndexKey);
        if (hashedTokens == null || hashedTokens.isEmpty()) {
            return;
        }

        for (String hashedToken : hashedTokens) {
            RefreshToken refreshToken = findByHashedToken(hashedToken);
            redisTemplate.delete(TOKEN_KEY_PREFIX + hashedToken);
            if (refreshToken != null && refreshToken.getSessionId() != null) {
                stringRedisTemplate.opsForSet().remove(SESSION_INDEX_PREFIX + refreshToken.getSessionId(), hashedToken);
            }
        }
        stringRedisTemplate.delete(userIndexKey);
    }

    /**
     * Revoke all tokens in a session family (breach response).
     * Used when replay attack or fingerprint mismatch is detected.
     */
    @Transactional
    public void revokeAllTokensInSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String sessionIndexKey = SESSION_INDEX_PREFIX + sessionId;

        Set<String> hashedTokens = stringRedisTemplate.opsForSet().members(sessionIndexKey);
        if (hashedTokens == null || hashedTokens.isEmpty()) {
            return;
        }

        for (String hashedToken : hashedTokens) {
            RefreshToken refreshToken = findByHashedToken(hashedToken);
            if (refreshToken != null) {
                refreshToken.setRevoked(true);
                saveToken(hashedToken, refreshToken);
            }
        }
    }

    /**
     * Validate token with session fingerprinting (CRITICAL for cookie theft prevention).
     * This prevents users from using stolen cookies from other sessions.
     */
    public boolean validateTokenWithSession(String token, String userId, HttpServletRequest request) {
        if (token == null || userId == null) {
            return false;
        }

        String hashedToken = hashToken(token);
        RefreshToken refreshToken = findByHashedToken(hashedToken);
        if (refreshToken == null) {
            return false;
        }

        // Check if token has been revoked (replay detection)
        if (refreshToken.isRevoked()) {
            return false;
        }

        // Check if token belongs to the correct user
        if (!refreshToken.getUserId().equalsIgnoreCase(userId.toLowerCase())) {
            return false;
        }

        // CRITICAL: Check session fingerprint for cookie theft prevention
        if (!"no-fingerprint".equals(refreshToken.getSessionFingerprint())) {
            String currentFingerprint = generateSessionFingerprint(request);
            if (!currentFingerprint.equals(refreshToken.getSessionFingerprint())) {
                // Fingerprint mismatch - cookie theft detected, revoke entire session family
                String sessionId = refreshToken.getSessionId();
                if (sessionId != null) {
                    revokeAllTokensInSession(sessionId);
                }
                return false;
            }
        }

        // Check if token has expired
        long now = System.currentTimeMillis();
        if (now > refreshToken.getExpirationTime()) {
            deleteToken(hashedToken, refreshToken);
            return false;
        }

        return true;
    }

    /**
     * Get user ID associated with a token.
     */
    public String getUserIdByToken(String token) {
        if (token == null) {
            return null;
        }
        String hashedToken = hashToken(token);
        RefreshToken refreshToken = findByHashedToken(hashedToken);
        return refreshToken != null ? refreshToken.getUserId() : null;
    }

    /**
     * Get session ID for a token (for tracking session families).
     */
    public String getSessionIdByToken(String token) {
        if (token == null) {
            return null;
        }
        String hashedToken = hashToken(token);
        RefreshToken refreshToken = findByHashedToken(hashedToken);
        return refreshToken != null ? refreshToken.getSessionId() : null;
    }

    /**
     * Refresh token rotation with atomic operations and breach detection.
     * Returns the new refresh token, or null if breach detected.
     */
    @Transactional
    public String rotateToken(String oldToken, String userId, HttpServletRequest request) {
        if (oldToken == null || userId == null) {
            return null;
        }

        String hashedOldToken = hashToken(oldToken);
        RefreshToken existingToken = findByHashedToken(hashedOldToken);

        if (existingToken == null) {
            // Token doesn't exist - either expired or already used (replay attack)
            return null;
        }

        if (existingToken.isRevoked()) {
            // Token was already revoked - replay attack detected
            String sessionId = existingToken.getSessionId();
            if (sessionId != null) {
                revokeAllTokensInSession(sessionId);
            }
            return null;
        }

        // Verify user ownership
        if (!existingToken.getUserId().equalsIgnoreCase(userId.toLowerCase())) {
            return null;
        }

        // Check fingerprint
        if (!"no-fingerprint".equals(existingToken.getSessionFingerprint())) {
            String currentFingerprint = generateSessionFingerprint(request);
            if (!currentFingerprint.equals(existingToken.getSessionFingerprint())) {
                // Fingerprint mismatch - potential session hijacking
                String sessionId = existingToken.getSessionId();
                if (sessionId != null) {
                    revokeAllTokensInSession(sessionId);
                }
                return null;
            }
        }

        // Check expiration
        long now = System.currentTimeMillis();
        if (now > existingToken.getExpirationTime()) {
            deleteToken(hashedOldToken, existingToken);
            return null;
        }

        // Mark old token as revoked BEFORE creating new one (atomic breach prevention)
        existingToken.setRevoked(true);
        saveToken(hashedOldToken, existingToken);

        // Generate new token in same session family
        String newToken = UUID.randomUUID().toString();
        String hashedNewToken = hashToken(newToken);
        long newExpirationTime = System.currentTimeMillis() + (refreshExpirySeconds * 1000L);
        String sessionFingerprint = generateSessionFingerprint(request);
        String normalizedUserId = userId.toLowerCase();
        String sessionId = existingToken.getSessionId() != null
                ? existingToken.getSessionId()
                : generateSessionId(); // Keep same session family

        RefreshToken newRefreshToken = new RefreshToken(
                hashedNewToken,
                normalizedUserId,
                newExpirationTime,
                refreshExpirySeconds,
                sessionFingerprint,
                sessionId
        );
        newRefreshToken.setRotationCount(existingToken.getRotationCount() + 1);

        saveToken(hashedNewToken, newRefreshToken);
        indexToken(hashedNewToken, normalizedUserId, sessionId);

        return newToken;
    }
}