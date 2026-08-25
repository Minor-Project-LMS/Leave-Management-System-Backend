package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.AuthService;
import com.lms.Leave_Management_System_Backend.service.JwtBlacklistService;
import com.lms.Leave_Management_System_Backend.service.JwtUtil;
import com.lms.Leave_Management_System_Backend.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@SuppressWarnings("unchecked")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    @Value("${cookie.same-site:Lax}")
    private String cookieSameSite;

    public AuthController(
            AuthService authService,
            JwtUtil jwtUtil,
            RefreshTokenService refreshTokenService) {

        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (req == null ||
                req.getEmail() == null ||
                req.getPassword() == null ||
                req.getEmail().isBlank() ||
                req.getPassword().isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(
                            false,
                            "Email and password are required"
                    ));
        }

        UserDto user = authService.authenticate(
                req.getEmail(),
                req.getPassword()
        );

        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(
                            false,
                            "Invalid email or password"
                    ));
        }

        String accessToken = jwtUtil.generateAccessToken(user);

        // Generate random UUID as refresh token, store with session fingerprinting
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenService.store(refreshToken, user.getEmail(), request);

        // Set HttpOnly, Secure, SameSite cookie scoped to /api/v1/auth
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/api/v1/auth"); // Scoped to auth endpoints per OpenAPI spec
        cookie.setMaxAge((int) (7 * 24 * 60 * 60));
        cookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(cookie);

        // Return only access token in response body (refresh token is in cookie)
        return ResponseEntity.ok(
                new ApiResponse<LoginResponse>(
                        true,
                        new LoginResponse(user, accessToken)
                )
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {

        authService.generateOtpForEmail(req.getEmail());

        // Always return 200 regardless of whether email exists (account enumeration prevention)
        return ResponseEntity.ok(
            new ApiResponse<>(
                true,
                "If an account exists for this email, a reset link has been sent."
            )
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {

        boolean valid =
                authService.verifyOtp(
                        req.getEmail(),
                        req.getOtp()
                );

        if (!valid) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiErrorResponse(
                            "INVALID_OTP",
                            "Invalid or expired OTP",
                            "/auth/reset-password"
                    ));
        }

        boolean resetSuccess = authService.resetPassword(
                req.getEmail(),
                req.getNewPassword()
        );

        if (!resetSuccess) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiErrorResponse(
                            "RESET_FAILED",
                            "Password reset failed",
                            "/auth/reset-password"
                    ));
        }

        return ResponseEntity.ok(
            new ApiResponse<>(
                true,
                "Password reset successful"
            )
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract refresh token from HttpOnly cookie
        String refreshToken = extractRefreshTokenFromCookie(request);

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(
                            false,
                            "Missing refresh token cookie"
                    ));
        }

        // Look up token in Redis with session validation
        String userId = refreshTokenService.getUserIdByToken(refreshToken);
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(
                            false,
                            "Invalid or expired refresh token"
                    ));
        }

        // CRITICAL SECURITY FIX: Validate session fingerprint before proceeding
        if (!refreshTokenService.validateTokenWithSession(refreshToken, userId, request)) {
            // Session fingerprint mismatch - potential cookie theft
            // Revoke all tokens in this session family
            String sessionId = refreshTokenService.getSessionIdByToken(refreshToken);
            if (sessionId != null) {
                refreshTokenService.revokeAllTokensInSession(sessionId);
            }

            // Clear the cookie
            Cookie clearCookie = new Cookie("refreshToken", "");
            clearCookie.setHttpOnly(true);
            clearCookie.setSecure(false);
            clearCookie.setPath("/api/v1/auth");
            clearCookie.setMaxAge(0);
            clearCookie.setAttribute("SameSite", cookieSameSite);
            response.addCookie(clearCookie);

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(
                            false,
                            "Session validation failed - possible security violation"
                    ));
        }

        UserDto user = authService.getUserByEmail(userId);
        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(
                            false,
                            "User not found"
                    ));
        }

        // Perform atomic Refresh Token Rotation (RTR) with breach detection
        String newRefreshToken = refreshTokenService.rotateToken(refreshToken, userId, request);

        if (newRefreshToken == null) {
            // Rotation failed - either replay attack detected or token expired
            // Clear the cookie to force re-authentication
            Cookie clearCookie = new Cookie("refreshToken", "");
            clearCookie.setHttpOnly(true);
            clearCookie.setSecure(false);
            clearCookie.setPath("/api/v1/auth");
            clearCookie.setMaxAge(0);
            clearCookie.setAttribute("SameSite", cookieSameSite);
            response.addCookie(clearCookie);

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(
                            false,
                            "Session expired or security violation detected - please login again"
                    ));
        }

        // Set new refresh token cookie with updated security flags
        Cookie newCookie = new Cookie("refreshToken", newRefreshToken);
        newCookie.setHttpOnly(true);
        newCookie.setSecure(false); // Set to true in production with HTTPS
        newCookie.setPath("/api/v1/auth");
        newCookie.setMaxAge((int) (7 * 24 * 60 * 60));
        newCookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(newCookie);

        String newAccessToken = jwtUtil.generateAccessToken(user);

        // Return only the new access token in response body
        return ResponseEntity.ok(
                new ApiResponse<LoginResponse>(
                        true,
                        new LoginResponse(user, newAccessToken)
                )
        );
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @PostMapping("/logout")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<?> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("Logout request received");

        // Blacklist the current access token if present
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            log.info("Access token found in Authorization header");
            
            if (jwtBlacklistService != null) {
                long remainingTime = jwtUtil.getRemainingExpirationTime(accessToken);
                log.info("Token remaining time: {} seconds", remainingTime);
                
                if (remainingTime > 0) {
                    jwtBlacklistService.blacklistToken(accessToken, remainingTime);
                    log.info("Access token blacklisted successfully");
                } else {
                    log.warn("Token has no remaining time, skipping blacklist");
                }
            } else {
                log.warn("JwtBlacklistService is not available - token cannot be blacklisted");
            }
        } else {
            log.warn("No access token found in Authorization header");
        }

        // Revoke refresh token
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                // CRITICAL: Validate session before logout to prevent cookie theft attacks
                String userId = refreshTokenService.getUserIdByToken(refreshToken);
                if (userId != null) {
                    if (refreshTokenService.validateTokenWithSession(refreshToken, userId, request)) {
                        // Revoke all tokens in the session family for security
                        String sessionId = refreshTokenService.getSessionIdByToken(refreshToken);
                        if (sessionId != null) {
                            refreshTokenService.revokeAllTokensInSession(sessionId);
                            log.info("All tokens in session {} revoked", sessionId);
                        } else {
                            refreshTokenService.revoke(refreshToken);
                            log.info("Refresh token revoked");
                        }
                    }
                } else {
                    refreshTokenService.revoke(refreshToken);
                    log.info("Refresh token revoked (no user ID found)");
                }
            } catch (Exception ex) {
                log.error("Logout revocation failed", ex);
            }
        } else {
            log.warn("No refresh token found in cookies");
        }

        // Clear the refresh token cookie scoped to /api/v1/auth.
        // This now always runs, regardless of what happened above.
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(cookie);

        // Add header to instruct client to clear access token
        response.setHeader("X-Clear-Auth", "true");

        log.info("Logout completed successfully");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        // Check if self-registration is enabled (would check system settings in real implementation)
        boolean selfRegistrationEnabled = false; // Default disabled per OpenAPI spec

        if (!selfRegistrationEnabled) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiErrorResponse(
                            "SELF_REGISTRATION_DISABLED",
                            "Contact HR to get enabled.",
                            "/auth/register"
                    ));
        }

        // Validate and create user
        UserDto user = authService.registerUser(req);

        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}