package com.lms.Leave_Management_System_Backend.dto;

public class TeamLeaveSummary {
    private Integer categoryId;
    private String categoryName;
    private Double totalDays;

    // Constructors
    public TeamLeaveSummary() {}

    public TeamLeaveSummary(Integer categoryId, String categoryName, Double totalDays) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.totalDays = totalDays;
    }

    // Getters and Setters
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

    public Double getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(Double totalDays) {
        this.totalDays = totalDays;
    }
}