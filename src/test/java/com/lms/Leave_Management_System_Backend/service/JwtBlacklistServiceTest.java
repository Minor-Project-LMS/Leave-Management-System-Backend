package com.lms.Leave_Management_System_Backend.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("Requires Redis connection - integration test only")
class JwtBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtBlacklistService jwtBlacklistService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        jwtBlacklistService = new JwtBlacklistService(redisTemplate);
    }

    @Test
    void blacklistToken_ShouldAddTokenToBlacklist() {
        String token = "test.jwt.token";
        long expirationTime = 900L; // 15 minutes

        jwtBlacklistService.blacklistToken(token, expirationTime);

        verify(valueOperations).set(eq("jwt:blacklist:" + token), eq("blacklisted"), eq(expirationTime), eq(TimeUnit.SECONDS));
    }

    @Test
    void isTokenBlacklisted_WithBlacklistedToken_ShouldReturnTrue() {
        String token = "test.jwt.token";
        when(redisTemplate.hasKey("jwt:blacklist:" + token)).thenReturn(true);

        boolean result = jwtBlacklistService.isTokenBlacklisted(token);

        assertTrue(result);
        verify(redisTemplate).hasKey("jwt:blacklist:" + token);
    }

    @Test
    void isTokenBlacklisted_WithNonBlacklistedToken_ShouldReturnFalse() {
        String token = "test.jwt.token";
        when(redisTemplate.hasKey("jwt:blacklist:" + token)).thenReturn(false);

        boolean result = jwtBlacklistService.isTokenBlacklisted(token);

        assertFalse(result);
        verify(redisTemplate).hasKey("jwt:blacklist:" + token);
    }

    @Test
    void isTokenBlacklisted_WithNullResponse_ShouldReturnFalse() {
        String token = "test.jwt.token";
        when(redisTemplate.hasKey("jwt:blacklist:" + token)).thenReturn(null);

        boolean result = jwtBlacklistService.isTokenBlacklisted(token);

        assertFalse(result);
    }

    @Test
    void removeFromBlacklist_ShouldDeleteToken() {
        String token = "test.jwt.token";

        jwtBlacklistService.removeFromBlacklist(token);

        verify(redisTemplate).delete("jwt:blacklist:" + token);
    }
}
