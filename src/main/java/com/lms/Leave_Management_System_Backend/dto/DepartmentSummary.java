package com.lms.Leave_Management_System_Backend.dto;

public class DepartmentSummary {

    private Integer departmentId;
    private String departmentName;
    private Integer totalEmployees;
    private Double totalLeaveDays;
    private Double avgLeaveDaysPerEmployee;
    private Double approvalRate;
    // Leave days actually used as a percentage of what the department's
    // employees were entitled to this year (opening balance + accrued +
    // carried forward). The HR Dashboard's "Utilization" column used to
    // show approvalRate in this slot, which is a different thing.
    private Double utilizationPct;

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

    public Double getUtilizationPct() {
        return utilizationPct;
    }

    public void setUtilizationPct(Double utilizationPct) {
        this.utilizationPct = utilizationPct;
    }

    public Double getApprovalRate() {
        return approvalRate;
    }

    public void setApprovalRate(Double approvalRate) {
        this.approvalRate = approvalRate;
    }
}