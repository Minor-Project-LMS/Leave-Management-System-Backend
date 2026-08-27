package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotNull;

public class LeaveQuotaRequest {
    
    @NotNull
    private Integer categoryId;
    
    @NotNull
    private Integer fiscalYear;
    
    @NotNull
    private Double quota;

    // Getters and Setters
    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public Double getQuota() {
        return quota;
    }

    public void setQuota(Double quota) {
        this.quota = quota;
    }
}