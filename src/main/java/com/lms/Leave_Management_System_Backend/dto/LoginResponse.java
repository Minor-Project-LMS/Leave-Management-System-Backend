package com.lms.Leave_Management_System_Backend.dto;

public class LoginResponse extends ApiResponse {
    private UserDto user;
    private String accessToken;
    private String refreshToken;

    public LoginResponse() {
    }

    public LoginResponse(boolean success, String message, UserDto user) {
        super(success, message);
        this.user = user;
    }

    public LoginResponse(boolean success, String message, UserDto user, String accessToken, String refreshToken) {
        super(success, message);
        this.user = user;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
