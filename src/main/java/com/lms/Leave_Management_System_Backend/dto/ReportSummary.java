package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class ReportSummary {

    private Double totalLeavesTaken;
    private Integer totalEmployees;
    private Double avgLeavePerEmployee;
    private Double approvalRate;
    private Integer pendingRequests;
    private List<String> insights;
    // Leave days used as a percentage of what employees were entitled to
    // this year (opening + accrued + carried forward). Feeds the HR
    // Dashboard's "Leave Utilization" card, which previously displayed the
    // approval rate under that label.
    private Double utilizationPct;

    // Getters and Setters
    public Double getTotalLeavesTaken() {
        return totalLeavesTaken;
    }

    public void setTotalLeavesTaken(Double totalLeavesTaken) {
        this.totalLeavesTaken = totalLeavesTaken;
    }

    public Integer getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(Integer totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public Double getAvgLeavePerEmployee() {
        return avgLeavePerEmployee;
    }

    public void setAvgLeavePerEmployee(Double avgLeavePerEmployee) {
        this.avgLeavePerEmployee = avgLeavePerEmployee;
    }

    public Double getApprovalRate() {
        return approvalRate;
    }

    public void setApprovalRate(Double approvalRate) {
        this.approvalRate = approvalRate;
    }

    public Integer getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(Integer pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    public Double getUtilizationPct() {
        return utilizationPct;
    }

    public void setUtilizationPct(Double utilizationPct) {
        this.utilizationPct = utilizationPct;
    }

    public List<String> getInsights() {
        return insights;
    }

    public void setInsights(List<String> insights) {
        this.insights = insights;
    }
}