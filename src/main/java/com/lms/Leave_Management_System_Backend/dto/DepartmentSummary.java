package com.lms.Leave_Management_System_Backend.dto;

public class DepartmentSummary {
    
    private Integer departmentId;
    private String departmentName;
    private Integer totalEmployees;
    private Double totalLeaveDays;
    private Double avgLeaveDaysPerEmployee;
    private Double approvalRate;

    // Getters and Setters
    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Integer getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(Integer totalEmployees) {
        this.totalEmployees = totalEmployees;
    }

    public Double getTotalLeaveDays() {
        return totalLeaveDays;
    }

    public void setTotalDays(Double totalLeaveDays) {
        this.totalLeaveDays = totalLeaveDays;
    }

    public Double getAvgLeaveDaysPerEmployee() {
        return avgLeaveDaysPerEmployee;
    }

    public void setAvgLeaveDaysPerEmployee(Double avgLeaveDaysPerEmployee) {
        this.avgLeaveDaysPerEmployee = avgLeaveDaysPerEmployee;
    }

    public Double getApprovalRate() {
        return approvalRate;
    }

    public void setApprovalRate(Double approvalRate) {
        this.approvalRate = approvalRate;
    }
}