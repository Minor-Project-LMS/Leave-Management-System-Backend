package com.lms.Leave_Management_System_Backend.dto;

import java.math.BigDecimal;
import java.util.Map;

public class DashboardSummary {
    private String userName;
    private String role;
    private String departmentName;
    private Map<String, BigDecimal> leaveBalances;
    private Integer pendingApprovalsCount;
    private Integer upcomingLeavesCount;
    private Integer teamOnLeaveToday;

    public DashboardSummary() {
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Map<String, BigDecimal> getLeaveBalances() {
        return leaveBalances;
    }

    public void setLeaveBalances(Map<String, BigDecimal> leaveBalances) {
        this.leaveBalances = leaveBalances;
    }

    public Integer getPendingApprovalsCount() {
        return pendingApprovalsCount;
    }

    public void setPendingApprovalsCount(Integer pendingApprovalsCount) {
        this.pendingApprovalsCount = pendingApprovalsCount;
    }

    public Integer getUpcomingLeavesCount() {
        return upcomingLeavesCount;
    }

    public void setUpcomingLeavesCount(Integer upcomingLeavesCount) {
        this.upcomingLeavesCount = upcomingLeavesCount;
    }

    public Integer getTeamOnLeaveToday() {
        return teamOnLeaveToday;
    }

    public void setTeamOnLeaveToday(Integer teamOnLeaveToday) {
        this.teamOnLeaveToday = teamOnLeaveToday;
    }
}
