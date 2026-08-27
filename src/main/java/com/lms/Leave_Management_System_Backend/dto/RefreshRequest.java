package com.lms.Leave_Management_System_Backend.dto;

/**
 * DEPRECATED: Refresh tokens are now extracted from HttpOnly cookies.
 * This DTO is no longer used in the Redis-backed authentication system.
 */
@Deprecated
public class RefreshRequest {
    private String refreshToken;

    public RefreshRequest() {
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
