package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private static final String AGENT = "JUnit-Browser/1.0";
    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    private RedisTemplate<String, RefreshToken> redis;
    private ValueOperations<String, RefreshToken> valueOps;
    private StringRedisTemplate stringRedis;
    private SetOperations<String, String> setOps;
    private RefreshTokenService service;
    private MockHttpServletRequest request;

    static String sha256(String value) throws Exception {
        return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String key(String token) throws Exception {
        return "refresh_token:" + sha256(token);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.getConnectionFactory()).thenReturn(mock(RedisConnectionFactory.class));

        service = new RefreshTokenService(redis);

        stringRedis = mock(StringRedisTemplate.class);
        setOps = mock(SetOperations.class);
        when(stringRedis.opsForSet()).thenReturn(setOps);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedis);
        ReflectionTestUtils.setField(service, "refreshExpirySeconds", 18000L);

        request = new MockHttpServletRequest();
        request.addHeader("User-Agent", AGENT);
    }

    private RefreshToken stored(String userId, long expiresAt) throws Exception {
        return new RefreshToken(sha256(TOKEN), userId, expiresAt, 18000, sha256(AGENT), "session-1");
    }

    private long future() {
        return System.currentTimeMillis() + 60_000;
    }

    @Nested
    class Store {

        @Test
        @DisplayName("stores the hashed token (never the raw value) and indexes it by user and session")
        void storesHashedToken() throws Exception {
            service.store(TOKEN, "Asha@LMS.com", request);

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(valueOps).set(eq(key(TOKEN)), saved.capture(), any(Duration.class));
            assertThat(saved.getValue().getTokenValue()).isEqualTo(sha256(TOKEN)).isNotEqualTo(TOKEN);
            assertThat(saved.getValue().getUserId()).isEqualTo("asha@lms.com");
            assertThat(saved.getValue().getSessionFingerprint()).isEqualTo(sha256(AGENT));
            assertThat(saved.getValue().getSessionId()).isNotBlank();
            verify(setOps).add("refresh_token:user:asha@lms.com", sha256(TOKEN));
            verify(setOps).add(eq("refresh_token:session:" + saved.getValue().getSessionId()), eq(sha256(TOKEN)));
        }

        @Test
        void nullsAreIgnored() {
            service.store(null, "a", request);
            service.store("t", null, request);
            verifyNoInteractions(valueOps, setOps);
        }
    }

    @Nested
    class Validate {

        @Test
        void validToken() throws Exception {
            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));

            assertThat(service.validate(TOKEN, "ASHA@lms.com", request)).isTrue();
            assertThat(service.validateTokenWithSession(TOKEN, "asha@lms.com", request)).isTrue();
        }

        @Test
        void unknownToken() {
            assertThat(service.validate(TOKEN, "asha@lms.com", request)).isFalse();
            assertThat(service.validateTokenWithSession(TOKEN, "asha@lms.com", request)).isFalse();
            assertThat(service.validate(null, "a", request)).isFalse();
        }

        @Test
        void revokedToken() throws Exception {
            RefreshToken t = stored("asha@lms.com", future());
            t.setRevoked(true);
            when(valueOps.get(key(TOKEN))).thenReturn(t);

            assertThat(service.validate(TOKEN, "asha@lms.com", request)).isFalse();
        }

        @Test
        void belongsToAnotherUser() throws Exception {
            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));
            assertThat(service.validateTokenWithSession(TOKEN, "mallory@lms.com", request)).isFalse();
        }

        @Test
        @DisplayName("a cookie replayed from a different browser revokes the whole session family")
        void fingerprintMismatchRevokesSession() throws Exception {
            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));
            when(setOps.members("refresh_token:session:session-1")).thenReturn(Set.of(sha256(TOKEN)));
            MockHttpServletRequest thief = new MockHttpServletRequest();
            thief.addHeader("User-Agent", "Evil-Browser");

            assertThat(service.validateTokenWithSession(TOKEN, "asha@lms.com", thief)).isFalse();

            ArgumentCaptor<RefreshToken> resaved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(valueOps).set(eq(key(TOKEN)), resaved.capture(), any(Duration.class));
            assertThat(resaved.getValue().isRevoked()).isTrue();
        }

        @Test
        void noFingerprintSentinelSkipsCheck() throws Exception {
            RefreshToken t = new RefreshToken(sha256(TOKEN), "asha@lms.com", future(), 18000, "no-fingerprint", "s");
            when(valueOps.get(key(TOKEN))).thenReturn(t);
            MockHttpServletRequest other = new MockHttpServletRequest();

            assertThat(service.validate(TOKEN, "asha@lms.com", other)).isTrue();
        }

        @Test
        void expiredTokenIsDeleted() throws Exception {
            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", System.currentTimeMillis() - 1000));

            assertThat(service.validate(TOKEN, "asha@lms.com", request)).isFalse();
            verify(redis).delete(key(TOKEN));
            verify(setOps).remove("refresh_token:user:asha@lms.com", sha256(TOKEN));
        }
    }

    @Nested
    class Rotate {

        @Test
        @DisplayName("issues a new token in the same session and revokes the old one")
        void rotates() throws Exception {
            RefreshToken old = stored("asha@lms.com", future());
            when(valueOps.get(key(TOKEN))).thenReturn(old);

            String newToken = service.rotateToken(TOKEN, "asha@lms.com", request);

            assertThat(newToken).isNotNull().isNotEqualTo(TOKEN);
            assertThat(old.isRevoked()).isTrue();
            ArgumentCaptor<RefreshToken> fresh = ArgumentCaptor.forClass(RefreshToken.class);
            verify(valueOps).set(eq(key(newToken)), fresh.capture(), any(Duration.class));
            assertThat(fresh.getValue().getSessionId()).isEqualTo("session-1");
            assertThat(fresh.getValue().getRotationCount()).isEqualTo(1L);
            assertThat(fresh.getValue().isRevoked()).isFalse();
        }

        @Test
        @DisplayName("re-using an already rotated token is treated as a replay attack")
        void replayRevokesSession() throws Exception {
            RefreshToken old = stored("asha@lms.com", future());
            old.setRevoked(true);
            when(valueOps.get(key(TOKEN))).thenReturn(old);
            when(setOps.members("refresh_token:session:session-1")).thenReturn(Set.of());

            assertThat(service.rotateToken(TOKEN, "asha@lms.com", request)).isNull();
            verify(setOps).members("refresh_token:session:session-1");
        }

        @Test
        void unknownWrongUserExpiredOrNull() throws Exception {
            assertThat(service.rotateToken(TOKEN, "asha@lms.com", request)).isNull();
            assertThat(service.rotateToken(null, "asha@lms.com", request)).isNull();

            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));
            assertThat(service.rotateToken(TOKEN, "mallory@lms.com", request)).isNull();

            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", System.currentTimeMillis() - 1));
            assertThat(service.rotateToken(TOKEN, "asha@lms.com", request)).isNull();
        }

        @Test
        void differentBrowserCannotRotate() throws Exception {
            when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));
            MockHttpServletRequest thief = new MockHttpServletRequest();
            thief.addHeader("User-Agent", "Evil");

            assertThat(service.rotateToken(TOKEN, "asha@lms.com", thief)).isNull();
        }
    }

    @Test
    void revokeDeletesTokenAndIndexes() throws Exception {
        when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));

        service.revoke(TOKEN);

        verify(redis).delete(key(TOKEN));
        verify(setOps).remove("refresh_token:session:session-1", sha256(TOKEN));
    }

    @Test
    void revokeByUserIdRemovesEveryToken() throws Exception {
        when(setOps.members("refresh_token:user:asha@lms.com")).thenReturn(Set.of("h1", "h2"));

        service.revokeByUserId("ASHA@lms.com");

        verify(redis).delete("refresh_token:h1");
        verify(redis).delete("refresh_token:h2");
        verify(stringRedis).delete("refresh_token:user:asha@lms.com");
    }

    @Test
    void revokeByUserIdWithNoTokensIsNoop() {
        service.revokeByUserId("asha@lms.com");
        service.revokeByUserId(null);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void lookups() throws Exception {
        when(valueOps.get(key(TOKEN))).thenReturn(stored("asha@lms.com", future()));

        assertThat(service.getUserIdByToken(TOKEN)).isEqualTo("asha@lms.com");
        assertThat(service.getSessionIdByToken(TOKEN)).isEqualTo("session-1");
        assertThat(service.getUserIdByToken("unknown")).isNull();
        assertThat(service.getUserIdByToken(null)).isNull();
        assertThat(service.getSessionIdByToken(null)).isNull();
    }
}
