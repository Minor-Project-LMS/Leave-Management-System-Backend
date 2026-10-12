package com.lms.Leave_Management_System_Backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JwtBlacklistServiceTest {

    private JwtBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new JwtBlacklistService();
        service.init();
    }

    @Test
    @DisplayName("in-memory only: blacklisted token is rejected until removed")
    void inMemoryBlacklist() {
        service.blacklistToken("tok", 60);
        assertThat(service.isTokenBlacklisted("tok")).isTrue();
        assertThat(service.isTokenBlacklisted("other")).isFalse();

        service.removeFromBlacklist("tok");
        assertThat(service.isTokenBlacklisted("tok")).isFalse();
    }

    @Test
    @DisplayName("an entry whose expiry has passed no longer blocks the token")
    void expiredEntryIgnored() {
        service.blacklistToken("tok", -5);
        assertThat(service.isTokenBlacklisted("tok")).isFalse();
    }

    @Test
    void cleanupRemovesOnlyExpired() {
        service.blacklistToken("old", -5);
        service.blacklistToken("fresh", 60);

        service.cleanupExpiredTokens();

        assertThat(service.isTokenBlacklisted("fresh")).isTrue();
        assertThat(service.isTokenBlacklisted("old")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesThroughToRedisWhenAvailable() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service.setRedisTemplate(redis);

        service.blacklistToken("tok", 120);

        verify(ops).set("jwt:blacklist:tok", "blacklisted", 120L, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("token known only to Redis (e.g. another instance) is still rejected")
    void readsFromRedis() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.hasKey("jwt:blacklist:tok")).thenReturn(true);
        service.setRedisTemplate(redis);

        assertThat(service.isTokenBlacklisted("tok")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Redis failures are non-fatal; memory still works")
    void redisFailureIsNonFatal() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));
        service.setRedisTemplate(redis);

        service.blacklistToken("tok", 60);
        assertThat(service.isTokenBlacklisted("tok")).isTrue();
        assertThat(service.isTokenBlacklisted("unknown")).isFalse();
        service.removeFromBlacklist("tok");
        assertThat(service.isTokenBlacklisted("tok")).isFalse();
    }
}
