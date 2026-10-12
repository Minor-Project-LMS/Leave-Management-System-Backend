package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.UserDto;
import com.lms.Leave_Management_System_Backend.service.AuthService;
import com.lms.Leave_Management_System_Backend.service.JwtBlacklistService;
import com.lms.Leave_Management_System_Backend.service.JwtUtil;
import com.lms.Leave_Management_System_Backend.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private JwtUtil jwtUtil;
    private RefreshTokenService refreshTokenService;
    private JwtBlacklistService blacklistService;
    private MockMvc mvc;
    private UserDto user;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtUtil = mock(JwtUtil.class);
        refreshTokenService = mock(RefreshTokenService.class);
        blacklistService = mock(JwtBlacklistService.class);

        AuthController controller = new AuthController(authService, jwtUtil, refreshTokenService);
        ReflectionTestUtils.setField(controller, "jwtBlacklistService", blacklistService);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Lax");
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        mvc = mockMvc(controller);

        user = new UserDto(1L, "Asha", "asha@lms.com", "EMPLOYEE");
    }

    @Nested
    class Login {

        @Test
        void missingFieldsIs400() throws Exception {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "asha@lms.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").value("Email and password are required"));
            verifyNoInteractions(authService);
        }

        @Test
        void wrongCredentialsIs401() throws Exception {
            when(authService.authenticate("asha@lms.com", "bad")).thenReturn(null);

            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "asha@lms.com", "password", "bad"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data").value("Invalid email or password"));
        }

        @Test
        @DisplayName("returns the access token in the body and the refresh token in an HttpOnly cookie")
        void success() throws Exception {
            when(authService.authenticate("asha@lms.com", "Secret@123")).thenReturn(user);
            when(jwtUtil.generateAccessToken(user)).thenReturn("access-jwt");

            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "asha@lms.com", "password", "Secret@123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                    .andExpect(jsonPath("$.data.user.email").value("asha@lms.com"))
                    .andExpect(cookie().exists("refreshToken"))
                    .andExpect(cookie().httpOnly("refreshToken", true))
                    .andExpect(cookie().secure("refreshToken", true))
                    .andExpect(cookie().path("refreshToken", "/api/v1/auth"))
                    .andExpect(cookie().maxAge("refreshToken", 7 * 24 * 60 * 60));

            verify(refreshTokenService).store(anyString(), eq("asha@lms.com"), any());
        }
    }

    @Nested
    class ForgotAndResetPassword {

        @Test
        void forgotPasswordUnknownEmailIs404() throws Exception {
            when(authService.generateOtpForEmail("x@lms.com")).thenReturn(false);

            mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "x@lms.com"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }

        @Test
        void forgotPasswordSuccess() throws Exception {
            when(authService.generateOtpForEmail("asha@lms.com")).thenReturn(true);

            mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "asha@lms.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void forgotPasswordInvalidEmailFailsValidation() throws Exception {
            mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "not-an-email"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        private String resetBody(String password) {
            return json(Map.of("email", "asha@lms.com", "otp", "123456", "newPassword", password));
        }

        @Test
        void resetWithInvalidOtp() throws Exception {
            when(authService.verifyOtp("asha@lms.com", "123456")).thenReturn(false);

            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(resetBody("NewPass@1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_OTP"));
            verify(authService, never()).resetPassword(anyString(), anyString());
        }

        @Test
        void resetFailure() throws Exception {
            when(authService.verifyOtp("asha@lms.com", "123456")).thenReturn(true);
            when(authService.resetPassword("asha@lms.com", "NewPass@1")).thenReturn(false);

            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(resetBody("NewPass@1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("RESET_FAILED"));
        }

        @Test
        void resetSuccess() throws Exception {
            when(authService.verifyOtp("asha@lms.com", "123456")).thenReturn(true);
            when(authService.resetPassword("asha@lms.com", "NewPass@1")).thenReturn(true);

            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(resetBody("NewPass@1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("Password reset successful"));
        }

        @Test
        @DisplayName("weak password is rejected by bean validation before any OTP check")
        void weakPasswordRejected() throws Exception {
            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(resetBody("password")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
            verifyNoInteractions(authService);
        }
    }

    @Nested
    class Refresh {

        private final Cookie cookie = new Cookie("refreshToken", "rt-1");

        @Test
        void missingCookie() throws Exception {
            mvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data").value("Missing refresh token cookie"));
        }

        @Test
        void unknownToken() throws Exception {
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn(null);

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data").value("Invalid or expired refresh token"));
        }

        @Test
        @DisplayName("session fingerprint mismatch revokes the session and clears the cookie")
        void sessionMismatch() throws Exception {
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn("asha@lms.com");
            when(refreshTokenService.validateTokenWithSession(eq("rt-1"), eq("asha@lms.com"), any())).thenReturn(false);
            when(refreshTokenService.getSessionIdByToken("rt-1")).thenReturn("sess");

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(cookie().maxAge("refreshToken", 0));
            verify(refreshTokenService).revokeAllTokensInSession("sess");
        }

        @Test
        void userNoLongerExists() throws Exception {
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn("asha@lms.com");
            when(refreshTokenService.validateTokenWithSession(any(), any(), any())).thenReturn(true);
            when(authService.getUserByEmail("asha@lms.com")).thenReturn(null);

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data").value("User not found"));
        }

        @Test
        void rotationFailure() throws Exception {
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn("asha@lms.com");
            when(refreshTokenService.validateTokenWithSession(any(), any(), any())).thenReturn(true);
            when(authService.getUserByEmail("asha@lms.com")).thenReturn(user);
            when(refreshTokenService.rotateToken(eq("rt-1"), eq("asha@lms.com"), any())).thenReturn(null);

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(cookie().maxAge("refreshToken", 0));
        }

        @Test
        void success() throws Exception {
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn("asha@lms.com");
            when(refreshTokenService.validateTokenWithSession(any(), any(), any())).thenReturn(true);
            when(authService.getUserByEmail("asha@lms.com")).thenReturn(user);
            when(refreshTokenService.rotateToken(eq("rt-1"), eq("asha@lms.com"), any())).thenReturn("rt-2");
            when(jwtUtil.generateAccessToken(user)).thenReturn("new-access");

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                    .andExpect(cookie().value("refreshToken", "rt-2"))
                    .andExpect(cookie().httpOnly("refreshToken", true));
        }
    }

    @Nested
    class Logout {

        @Test
        @DisplayName("blacklists the access token, revokes the session and clears the cookie")
        void fullLogout() throws Exception {
            when(jwtUtil.getRemainingExpirationTime("access-jwt")).thenReturn(600L);
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn("asha@lms.com");
            when(refreshTokenService.validateTokenWithSession(any(), any(), any())).thenReturn(true);
            when(refreshTokenService.getSessionIdByToken("rt-1")).thenReturn("sess");

            mvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer access-jwt")
                            .cookie(new Cookie("refreshToken", "rt-1")))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("X-Clear-Auth", "true"))
                    .andExpect(cookie().maxAge("refreshToken", 0));

            verify(blacklistService).blacklistToken("access-jwt", 600L);
            verify(refreshTokenService).revokeAllTokensInSession("sess");
        }

        @Test
        void expiredAccessTokenIsNotBlacklistedAndOrphanRefreshTokenIsRevoked() throws Exception {
            when(jwtUtil.getRemainingExpirationTime("access-jwt")).thenReturn(0L);
            when(refreshTokenService.getUserIdByToken("rt-1")).thenReturn(null);

            mvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer access-jwt")
                            .cookie(new Cookie("refreshToken", "rt-1")))
                    .andExpect(status().isNoContent());

            verify(blacklistService, never()).blacklistToken(anyString(), anyLong());
            verify(refreshTokenService).revoke("rt-1");
        }

        @Test
        void logoutStillSucceedsWhenRevocationThrows() throws Exception {
            when(refreshTokenService.getUserIdByToken("rt-1")).thenThrow(new RuntimeException("redis down"));

            mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", "rt-1")))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge("refreshToken", 0));
        }

        @Test
        void logoutWithNothingToRevoke() throws Exception {
            mvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isNoContent());
            verifyNoInteractions(blacklistService);
        }
    }

    @Test
    @DisplayName("self-registration is disabled")
    void registerIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "X", "email", "x@lms.com", "password", "Password@1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SELF_REGISTRATION_DISABLED"));
        verify(authService, never()).registerUser(any());
    }
}
