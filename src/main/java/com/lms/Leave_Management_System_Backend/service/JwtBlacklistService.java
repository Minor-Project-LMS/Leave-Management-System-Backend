package com.lms.Leave_Management_System_Backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class JwtBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistService.class);
    
    private RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    
    // Primary in-memory blacklist (always available)
    private final ConcurrentHashMap<String, Long> inMemoryBlacklist = new ConcurrentHashMap<>();

    public JwtBlacklistService() {
        log.info("JwtBlacklistService constructor called");
    }

    @Autowired(required = false)
    @Qualifier("stringRedisTemplate")
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate != null) {
            log.info("JwtBlacklistService: Redis backend available");
        } else {
            log.info("JwtBlacklistService: Using in-memory only (Redis not available)");
        }
    }

    @PostConstruct
    public void init() {
        log.info("JwtBlacklistService initialized with in-memory storage");
    }

    /**
     * Add a JWT token to the blacklist
     * @param token The JWT token to blacklist
     * @param expirationTimeInSeconds Time in seconds until the token would naturally expire
     */
    public void blacklistToken(String token, long expirationTimeInSeconds) {
        log.info("Blacklisting token with {} seconds remaining", expirationTimeInSeconds);
        
        // Always store in memory as primary
        long expiryTime = System.currentTimeMillis() + (expirationTimeInSeconds * 1000);
        inMemoryBlacklist.put(token, expiryTime);
        log.info("Token blacklisted in memory (primary storage)");
        
        // Also try to store in Redis if available
        if (redisTemplate != null) {
            try {
                String key = BLACKLIST_PREFIX + token;
                redisTemplate.opsForValue().set(key, "blacklisted", expirationTimeInSeconds, TimeUnit.SECONDS);
                log.info("Token also blacklisted in Redis (secondary storage)");
            } catch (Exception e) {
                log.warn("Failed to blacklist token in Redis (non-critical): {}", e.getMessage());
            }
        }
    }

    /**
     * Check if a JWT token is blacklisted
     * @param token The JWT token to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String token) {
        log.debug("Checking if token is blacklisted");
        
        // Check in-memory blacklist first (primary)
        Long expiryTime = inMemoryBlacklist.get(token);
        if (expiryTime != null) {
            if (System.currentTimeMillis() < expiryTime) {
                log.info("Token found in in-memory blacklist - REJECTING");
                return true;
            } else {
                // Clean up expired token
                inMemoryBlacklist.remove(token);
                log.debug("Removed expired token from in-memory blacklist");
            }
        }
        
        // Check Redis if available (secondary)
        if (redisTemplate != null) {
            try {
                String key = BLACKLIST_PREFIX + token;
                Boolean exists = redisTemplate.hasKey(key);
                if (exists != null && exists) {
                    log.info("Token found in Redis blacklist - REJECTING");
                    // Sync to memory for consistency
                    inMemoryBlacklist.put(token, System.currentTimeMillis() + (15 * 60 * 1000));
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to check Redis blacklist (non-critical): {}", e.getMessage());
            }
        }
        
        log.debug("Token is not blacklisted - ACCEPTING");
        return false;
    }

    /**
     * Remove a token from the blacklist (for testing/admin purposes)
     * @param token The JWT token to remove from blacklist
     */
    public void removeFromBlacklist(String token) {
        log.info("Removing token from blacklist");
        
        inMemoryBlacklist.remove(token);
        
        if (redisTemplate != null) {
            try {
                String key = BLACKLIST_PREFIX + token;
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Failed to remove token from Redis blacklist (non-critical): {}", e.getMessage());
            }
        }
    }
    
    /**
     * Clean up expired tokens from in-memory blacklist
     */
    public void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        int sizeBefore = inMemoryBlacklist.size();
        inMemoryBlacklist.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        int removed = sizeBefore - inMemoryBlacklist.size();
        log.debug("Cleaned up {} expired tokens from in-memory blacklist", removed);
    }
}
