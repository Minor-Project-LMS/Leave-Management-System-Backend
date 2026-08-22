package com.lms.Leave_Management_System_Backend.dto;

public class ManagerDashboardSummary {
    
    private Integer totalTeamSize;
    private Integer activeEmployees;
    private Integer pendingApprovals;
    private Boolean pendingUrgent;
    private Integer leavesThisMonth;
    private Double leavesThisMonthChangePct;
    private Double availableBalanceAvg;

    // Getters and Setters
    public Integer getTotalTeamSize() {
        return totalTeamSize;
    }

    public void setTotalTeamSize(Integer totalTeamSize) {
        this.totalTeamSize = totalTeamSize;
    }

    public Integer getActiveEmployees() {
        return activeEmployees;
    }

    public void setActiveEmployees(Integer activeEmployees) {
        this.activeEmployees = activeEmployees;
    }

    public Integer getPendingApprovals() {
        return pendingApprovals;
    }

    public void setPendingApprovals(Integer pendingApprovals) {
        this.pendingApprovals = pendingApprovals;
    }

    public Boolean getPendingUrgent() {
        return pendingUrgent;
    }

    public void setPendingUrgent(Boolean pendingUrgent) {
        this.pendingUrgent = pendingUrgent;
    }

    public Integer getLeavesThisMonth() {
        return leavesThisMonth;
    }

    public void setLeavesThisMonth(Integer leavesThisMonth) {
        this.leavesThisMonth = leavesThisMonth;
    }

    public Double getLeavesThisMonthChangePct() {
        return leavesThisMonthChangePct;
    }

    public void setLeavesThisMonthChangePct(Double leavesThisMonthChangePct) {
        this.leavesThisMonthChangePct = leavesThisMonthChangePct;
    }

    public Double getAvailableBalanceAvg() {
        return availableBalanceAvg;
    }

    public void setAvailableBalanceAvg(Double availableBalanceAvg) {
        this.availableBalanceAvg = availableBalanceAvg;
    }
}