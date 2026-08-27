package com.lms.Leave_Management_System_Backend.dto;

public class TeamCalendarEntry {
    private Long userId;
    private String fullName;
    private String avatarUrl;
    private Integer categoryId;
    private String categoryName;
    private String sessionType;

    // Constructors
    public TeamCalendarEntry() {}

    public TeamCalendarEntry(Long userId, String fullName, String avatarUrl, 
                           Integer categoryId, String categoryName, String sessionType) {
        this.userId = userId;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.sessionType = sessionType;
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }
}