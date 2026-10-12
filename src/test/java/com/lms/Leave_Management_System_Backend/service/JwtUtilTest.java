package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.UserDto;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtUtilTest {

    private static final String SECRET = "3G7zW5qqYmmvvbU2UcbHfMXoZ2AKdrvgcWL5eisngxi";

    private JwtUtil jwtUtil;
    private JwtBlacklistService blacklist;

    private JwtUtil newJwtUtil(String secret, long accessSeconds, long refreshSeconds) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "accessExpirySeconds", accessSeconds);
        ReflectionTestUtils.setField(util, "refreshExpirySeconds", refreshSeconds);
        ReflectionTestUtils.setField(util, "jwtBlacklistService", blacklist);
        util.init();
        return util;
    }

    private static UserDto user() {
        return new UserDto(1L, "Asha Rao", "asha@lms.com", "EMPLOYEE");
    }

    @BeforeEach
    void setUp() {
        blacklist = mock(JwtBlacklistService.class);
        jwtUtil = newJwtUtil(SECRET, 900, 18000);
    }

    @Test
    @DisplayName("access token carries subject, email, name and role claims")
    void accessTokenClaims() {
        String token = jwtUtil.generateAccessToken(user());

        Claims claims = jwtUtil.getClaims(token);
        assertThat(claims.getSubject()).isEqualTo("asha@lms.com");
        assertThat(claims.get("email", String.class)).isEqualTo("asha@lms.com");
        assertThat(claims.get("name", String.class)).isEqualTo("Asha Rao");
        assertThat(claims.get("role", String.class)).isEqualTo("EMPLOYEE");
        assertThat(jwtUtil.getEmailFromToken(token)).isEqualTo("asha@lms.com");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo("asha@lms.com");
    }

    @Test
    void validTokenValidates() {
        assertThat(jwtUtil.validateToken(jwtUtil.generateAccessToken(user()))).isTrue();
    }

    @Test
    void refreshTokenHasSubject() {
        String token = jwtUtil.generateRefreshToken("asha@lms.com");
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getEmailFromToken(token)).isEqualTo("asha@lms.com");
    }

    @Test
    void nullOrBlankTokenIsInvalid() {
        assertThat(jwtUtil.validateToken(null)).isFalse();
        assertThat(jwtUtil.validateToken("  ")).isFalse();
    }

    @Test
    void blacklistedTokenIsInvalid() {
        String token = jwtUtil.generateAccessToken(user());
        when(blacklist.isTokenBlacklisted(token)).thenReturn(true);

        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtUtil.generateAccessToken(user());
        String otherToken = jwtUtil.generateAccessToken(new UserDto(2L, "Mallory", "mallory@lms.com", "HR_ADMIN"));
        String[] original = token.split("\\.");
        String[] other = otherToken.split("\\.");
        // Swap in a different payload but keep the original signature.
        String tampered = original[0] + "." + other[1] + "." + original[2];

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretIsInvalid() {
        JwtUtil other = newJwtUtil("a-completely-different-secret-value-123456", 900, 18000);
        assertThat(jwtUtil.validateToken(other.generateAccessToken(user()))).isFalse();
    }

    @Test
    void expiredTokenIsInvalidAndHasNoRemainingTime() {
        JwtUtil shortLived = newJwtUtil(SECRET, -60, -60);
        String token = shortLived.generateAccessToken(user());

        assertThat(shortLived.validateToken(token)).isFalse();
        assertThat(shortLived.getRemainingExpirationTime(token)).isZero();
    }

    @Test
    void remainingExpirationTimeIsWithinConfiguredWindow() {
        long remaining = jwtUtil.getRemainingExpirationTime(jwtUtil.generateAccessToken(user()));
        assertThat(remaining).isBetween(890L, 900L);
    }

    @Test
    void garbageTokenHelpersAreSafe() {
        assertThat(jwtUtil.extractUserId("not-a-jwt")).isNull();
        assertThat(jwtUtil.getRemainingExpirationTime("not-a-jwt")).isZero();
        assertThatThrownBy(() -> jwtUtil.getClaims("not-a-jwt")).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a secret shorter than 32 bytes is zero-padded rather than rejected")
    void shortSecretIsPadded() {
        JwtUtil util = newJwtUtil("short", 900, 18000);
        assertThat(util.validateToken(util.generateAccessToken(user()))).isTrue();
    }

    @Test
    void worksWithoutBlacklistService() {
        blacklist = null;
        JwtUtil util = newJwtUtil(SECRET, 900, 18000);
        assertThat(util.validateToken(util.generateAccessToken(user()))).isTrue();
    }

    @Test
    void blacklistIsConsultedOnEveryValidation() {
        String token = jwtUtil.generateAccessToken(user());
        jwtUtil.validateToken(token);
        verify(blacklist).isTokenBlacklisted(anyString());
    }
}
