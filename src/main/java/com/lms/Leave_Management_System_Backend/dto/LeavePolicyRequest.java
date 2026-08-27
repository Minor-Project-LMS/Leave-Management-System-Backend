package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;

public class LeavePolicyRequest {
    private String policyName;
    private Integer categoryId;
    private Integer departmentId;
    private Double annualQuota;
    private Double maxCarryForward;
    private Integer minNoticeDays;
    private Integer maxConsecutiveDays;
    private String accrualFrequency;
    private LocalDate effectiveFrom;
    private String status;

    // Constructors
    public LeavePolicyRequest() {}

    // Getters and Setters
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public Double getAnnualQuota() {
        return annualQuota;
    }

    public void setAnnualQuota(Double annualQuota) {
        this.annualQuota = annualQuota;
    }

    public Double getMaxCarryForward() {
        return maxCarryForward;
    }

    public void setMaxCarryForward(Double maxCarryForward) {
        this.maxCarryForward = maxCarryForward;
    }

    public Integer getMinNoticeDays() {
        return minNoticeDays;
    }

    public void setMinNoticeDays(Integer minNoticeDays) {
        this.minNoticeDays = minNoticeDays;
    }

    public Integer getMaxConsecutiveDays() {
        return maxConsecutiveDays;
    }

    public void setMaxConsecutiveDays(Integer maxConsecutiveDays) {
        this.maxConsecutiveDays = maxConsecutiveDays;
    }

    public String getAccrualFrequency() {
        return accrualFrequency;
    }

    public void setAccrualFrequency(String accrualFrequency) {
        this.accrualFrequency = accrualFrequency;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}