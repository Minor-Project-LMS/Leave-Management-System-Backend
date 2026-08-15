package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.service.AuthService;
import com.lms.Leave_Management_System_Backend.service.JwtUtil;
import com.lms.Leave_Management_System_Backend.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

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
                    .body(new ApiResponse(
                            false,
                            "Invalid email or password"
                    ));
        }

        String accessToken = jwtUtil.generateAccessToken(user);

        // Generate random UUID as refresh token, store with session fingerprinting
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenService.store(refreshToken, user.getEmail(), request);

        // Set HttpOnly, Secure, SameSite cookie scoped to root path
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/"); // Root path - available across entire application
        cookie.setMaxAge((int) (7 * 24 * 60 * 60));
        cookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(cookie);

        // Return only access token in response body (refresh token is in cookie)
        return ResponseEntity.ok(
                new LoginResponse(
                        true,
                        "Login successful",
                        user,
                        accessToken
                )
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody ForgotPasswordRequest req) {

        boolean ok =
                authService.generateOtpForEmail(req.getEmail());

        if (!ok) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(
                            false,
                            "User not found"
                    ));
        }

        return ResponseEntity.ok(
                new ApiResponse(
                        true,
                        "If the email is registered, a reset code has been sent."
                )
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody ResetPasswordRequest req) {

        boolean valid =
                authService.verifyOtp(
                        req.getEmail(),
                        req.getOtp()
                );

        if (!valid) {
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse(
                            false,
                            "Invalid or expired OTP"
                    ));
        }

        authService.resetPassword(
                req.getEmail(),
                req.getNewPassword()
        );

        return ResponseEntity.ok(
                new ApiResponse(
                        true,
                        "Password has been reset successfully"
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
                    .body(new ApiResponse(
                            false,
                            "Missing refresh token cookie"
                    ));
        }

        // Look up token in Redis with session validation
        String userId = refreshTokenService.getUserIdByToken(refreshToken);
        if (userId == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(
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
            clearCookie.setPath("/");
            clearCookie.setMaxAge(0);
            clearCookie.setAttribute("SameSite", cookieSameSite);
            response.addCookie(clearCookie);
            
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(
                            false,
                            "Session validation failed - possible security violation"
                    ));
        }

        UserDto user = authService.getUserByEmail(userId);
        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(
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
            clearCookie.setPath("/"); // Root path - clear from entire application
            clearCookie.setMaxAge(0);
            clearCookie.setAttribute("SameSite", cookieSameSite);
            response.addCookie(clearCookie);
            
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(
                            false,
                            "Session expired or security violation detected - please login again"
                    ));
        }

        // Set new refresh token cookie with updated security flags
        Cookie newCookie = new Cookie("refreshToken", newRefreshToken);
        newCookie.setHttpOnly(true);
        newCookie.setSecure(false); // Set to true in production with HTTPS
        newCookie.setPath("/"); // Root path - available across entire application
        newCookie.setMaxAge((int) (7 * 24 * 60 * 60));
        newCookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(newCookie);

        String newAccessToken = jwtUtil.generateAccessToken(user);

        // Return only the new access token in response body
        return ResponseEntity.ok(
                new LoginResponse(
                        true,
                        "Token refreshed",
                        user,
                        newAccessToken
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
    public ResponseEntity<?> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken != null && !refreshToken.isBlank()) {
            // CRITICAL: Validate session before logout to prevent cookie theft attacks
            String userId = refreshTokenService.getUserIdByToken(refreshToken);
            if (userId != null) {
                if (!refreshTokenService.validateTokenWithSession(refreshToken, userId, request)) {
                    // Session validation failed - cookie theft suspected
                    // Still clear the cookie but don't reveal specific error
                    Cookie cookie = new Cookie("refreshToken", "");
                    cookie.setHttpOnly(true);
                    cookie.setSecure(false);
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    cookie.setAttribute("SameSite", cookieSameSite);
                    response.addCookie(cookie);
                    
                    return ResponseEntity.ok(
                            new ApiResponse(
                                    true,
                                    "Logged out successfully"
                            )
                    );
                }
                
                // Revoke all tokens in the session family for security
                String sessionId = refreshTokenService.getSessionIdByToken(refreshToken);
                if (sessionId != null) {
                    refreshTokenService.revokeAllTokensInSession(sessionId);
                } else {
                    refreshTokenService.revoke(refreshToken);
                }
            } else {
                refreshTokenService.revoke(refreshToken);
            }
        }

        // Clear the refresh token cookie scoped to root path
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/"); // Root path - clear from entire application
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", cookieSameSite);
        response.addCookie(cookie);

        return ResponseEntity.ok(
                new ApiResponse(
                        true,
                        "Logged out successfully"
                )
        );
    }
}