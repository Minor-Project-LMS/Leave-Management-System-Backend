package com.lms.Leave_Management_System_Backend.dto;

public class LeaveCategoryRequest {
    private String name;
    private Boolean paid;
    private Boolean requiresDocument;
    private Double defaultAnnualQuota;

    public LeaveCategoryRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getPaid() {
        return paid;
    }

    public void setPaid(Boolean paid) {
        this.paid = paid;
    }

    public Boolean getRequiresDocument() {
        return requiresDocument;
    }

    public void setRequiresDocument(Boolean requiresDocument) {
        this.requiresDocument = requiresDocument;
    }

    public Double getDefaultAnnualQuota() {
        return defaultAnnualQuota;
    }

    public void setDefaultAnnualQuota(Double defaultAnnualQuota) {
        this.defaultAnnualQuota = defaultAnnualQuota;
    }
}
