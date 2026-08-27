package com.lms.Leave_Management_System_Backend.dto;

public class DashboardSummary {
    private Double availableLeave;
    private Double usedLeave;
    private Integer pendingRequests;
    private Double compOffBalance;

    public DashboardSummary() {
    }

    public Double getAvailableLeave() {
        return availableLeave;
    }

    public void setAvailableLeave(Double availableLeave) {
        this.availableLeave = availableLeave;
    }

    public Double getUsedLeave() {
        return usedLeave;
    }

    public void setUsedLeave(Double usedLeave) {
        this.usedLeave = usedLeave;
    }

    public Integer getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(Integer pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    public Double getCompOffBalance() {
        return compOffBalance;
    }

    public void setCompOffBalance(Double compOffBalance) {
        this.compOffBalance = compOffBalance;
    }
}
