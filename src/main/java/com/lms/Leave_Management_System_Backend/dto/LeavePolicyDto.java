package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;

public class LeavePolicyDto {
    private Integer policyId;
    private String policyName;
    private String policyCode;
    private LeaveCategoryDto category;
    private DepartmentDto department;
    private Double annualQuota;
    private Double maxCarryForward;
    private Integer minNoticeDays;
    private Integer maxConsecutiveDays;
    private String accrualFrequency;
    private LocalDate effectiveFrom;
    private String status;

    // Constructors
    public LeavePolicyDto() {}

    // Getters and Setters
    public Integer getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Integer policyId) {
        this.policyId = policyId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public LeaveCategoryDto getCategory() {
        return category;
    }

    public void setCategory(LeaveCategoryDto category) {
        this.category = category;
    }

    public DepartmentDto getDepartment() {
        return department;
    }

    public void setDepartment(DepartmentDto department) {
        this.department = department;
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