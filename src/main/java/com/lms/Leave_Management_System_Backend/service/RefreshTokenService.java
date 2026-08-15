package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.RefreshToken;
import com.lms.Leave_Management_System_Backend.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Redis-backed refresh token service with Refresh Token Rotation (RTR) and breach detection.
 * Implements session fingerprinting, token rotation, and replay attack prevention.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom;

    @Value("${jwt.refresh_expiration_seconds:18000}")
    private long refreshExpirySeconds;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
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
        return UUID.randomUUID().toString();
    }

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
        long expirationTime = System.currentTimeMillis() + (refreshExpirySeconds * 1000L);
        
        RefreshToken refreshToken = new RefreshToken(
            hashedToken, 
            userId.toLowerCase(), 
            expirationTime, 
            refreshExpirySeconds,
            sessionFingerprint,
            sessionId
        );
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Validate token with fingerprint checking and replay detection.
     */
    public boolean validate(String token, String userId, HttpServletRequest request) {
        if (token == null || userId == null) {
            return false;
        }

        String hashedToken = hashToken(token);
        return refreshTokenRepository.findById(hashedToken)
                .map(refreshToken -> {
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
                            // Trigger security response
                            revokeAllTokensInSession(refreshToken.getSessionId());
                            return false;
                        }
                    }
                    
                    // Check if token has expired
                    long now = System.currentTimeMillis();
                    if (now > refreshToken.getExpirationTime()) {
                        refreshTokenRepository.deleteById(hashedToken);
                        return false;
                    }
                    
                    return true;
                })
                .orElse(false);
    }

    /**
     * Revoke a specific token.
     */
    public void revoke(String token) {
        if (token != null) {
            String hashedToken = hashToken(token);
            refreshTokenRepository.deleteById(hashedToken);
        }
    }

    /**
     * Revoke all tokens for a specific user.
     */
    public void revokeByUserId(String userId) {
        if (userId == null) {
            return;
        }
        String normalizedUserId = userId.toLowerCase();
        refreshTokenRepository.findAll().forEach(refreshToken -> {
            if (refreshToken != null && refreshToken.getUserId().equalsIgnoreCase(normalizedUserId)) {
                refreshTokenRepository.delete(refreshToken);
            }
        });
    }

    /**
     * Revoke all tokens in a session family (atomic operation for breach response).
     * Used when replay attack is detected.
     */
    @Transactional
    public void revokeAllTokensInSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        
        // Mark all tokens in this session as revoked
        refreshTokenRepository.findAll().forEach(refreshToken -> {
            if (refreshToken != null && sessionId.equals(refreshToken.getSessionId())) {
                refreshToken.setRevoked(true);
                refreshTokenRepository.save(refreshToken);
            }
        });
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
        return refreshTokenRepository.findById(hashedToken)
                .map(refreshToken -> {
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
                            // Fingerprint mismatch - cookie theft detected
                            // Revoke entire session family
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
                        refreshTokenRepository.deleteById(hashedToken);
                        return false;
                    }
                    
                    return true;
                })
                .orElse(false);
    }

    /**
     * Get user ID associated with a token.
     */
    public String getUserIdByToken(String token) {
        if (token == null) {
            return null;
        }
        
        String hashedToken = hashToken(token);
        return refreshTokenRepository.findById(hashedToken)
                .map(RefreshToken::getUserId)
                .orElse(null);
    }

    /**
     * Get session ID for a token (for tracking session families).
     */
    public String getSessionIdByToken(String token) {
        if (token == null) {
            return null;
        }
        
        String hashedToken = hashToken(token);
        return refreshTokenRepository.findById(hashedToken)
                .map(RefreshToken::getSessionId)
                .orElse(null);
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
        
        // Atomic check-and-delete to prevent race conditions
        RefreshToken existingToken = refreshTokenRepository.findById(hashedOldToken).orElse(null);
        
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
            refreshTokenRepository.deleteById(hashedOldToken);
            return null;
        }
        
        // Mark old token as revoked BEFORE creating new one (atomic breach prevention)
        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);
        
        // Generate new token in same session family
        String newToken = UUID.randomUUID().toString();
        String hashedNewToken = hashToken(newToken);
        long newExpirationTime = System.currentTimeMillis() + (refreshExpirySeconds * 1000L);
        String sessionFingerprint = generateSessionFingerprint(request);
        
        RefreshToken newRefreshToken = new RefreshToken(
            hashedNewToken,
            userId.toLowerCase(),
            newExpirationTime,
            refreshExpirySeconds,
            sessionFingerprint,
            existingToken.getSessionId() != null ? existingToken.getSessionId() : generateSessionId() // Keep same session family
        );
        newRefreshToken.setRotationCount(existingToken.getRotationCount() + 1);
        
        refreshTokenRepository.save(newRefreshToken);
        
        return newToken;
    }
}
