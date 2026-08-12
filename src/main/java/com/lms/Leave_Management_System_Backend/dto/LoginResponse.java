package com.lms.Leave_Management_System_Backend.dto;

public class LoginResponse extends ApiResponse {
    private UserDto user;

    public LoginResponse() {
    }

    public LoginResponse(boolean success, String message, UserDto user) {
        super(success, message);
        this.user = user;
    }

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }
}
