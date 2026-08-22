package com.lms.Leave_Management_System_Backend.dto;

public class TeamLeaveOverviewRow {
    
    private String department;
    private Integer totalMembers;
    private Integer onLeaveToday;
    private Integer leavesThisMonth;
    private Double availableBalanceAvg;

    // Constructor for simplified creation
    public TeamLeaveOverviewRow(String department, Integer totalMembers, Integer onLeaveToday, 
                                Integer leavesThisMonth, Double availableBalanceAvg) {
        this.department = department;
        this.totalMembers = totalMembers;
        this.onLeaveToday = onLeaveToday;
        this.leavesThisMonth = leavesThisMonth;
        this.availableBalanceAvg = availableBalanceAvg;
    }

    // Default constructor
    public TeamLeaveOverviewRow() {}

    // Getters and Setters
    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getTotalMembers() {
        return totalMembers;
    }

    public void setTotalMembers(Integer totalMembers) {
        this.totalMembers = totalMembers;
    }

    public Integer getOnLeaveToday() {
        return onLeaveToday;
    }

    public void setOnLeaveToday(Integer onLeaveToday) {
        this.onLeaveToday = onLeaveToday;
    }

    public Integer getLeavesThisMonth() {
        return leavesThisMonth;
    }

    public void setLeavesThisMonth(Integer leavesThisMonth) {
        this.leavesThisMonth = leavesThisMonth;
    }

    public Double getAvailableBalanceAvg() {
        return availableBalanceAvg;
    }

    public void setAvailableBalanceAvg(Double availableBalanceAvg) {
        this.availableBalanceAvg = availableBalanceAvg;
    }
}