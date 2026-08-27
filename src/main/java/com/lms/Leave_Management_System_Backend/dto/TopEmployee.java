package com.lms.Leave_Management_System_Backend.dto;

public class TopEmployee {
    
    private Integer userId;
    private String fullName;
    private String departmentName;
    private Double totalDaysTaken;

    // Getters and Setters
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Double getTotalDaysTaken() {
        return totalDaysTaken;
    }

    public void setTotalDaysTaken(Double totalDaysTaken) {
        this.totalDaysTaken = totalDaysTaken;
    }
}