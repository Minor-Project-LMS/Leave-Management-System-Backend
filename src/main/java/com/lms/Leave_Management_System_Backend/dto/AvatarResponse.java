package com.lms.Leave_Management_System_Backend.dto;

public class AvatarResponse {
    private String avatarUrl;

    public AvatarResponse(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}