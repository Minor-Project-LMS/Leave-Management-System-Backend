package com.lms.Leave_Management_System_Backend.dto;

public class LoginResponse {
    private UserDto user;
    private String accessToken;

    public LoginResponse() {
    }

    public LoginResponse(UserDto user, String accessToken) {
        this.user = user;
        this.accessToken = accessToken;
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
}
