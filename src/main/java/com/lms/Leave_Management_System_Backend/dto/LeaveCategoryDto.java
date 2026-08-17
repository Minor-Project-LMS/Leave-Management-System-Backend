package com.lms.Leave_Management_System_Backend.dto;

public class LeaveCategoryDto {
    private Integer id;
    private String name;
    private Boolean paid;
    private Boolean requiresDocument;
    private Double defaultAnnualQuota;
    private Boolean active;

    public LeaveCategoryDto() {
    }

    public LeaveCategoryDto(Integer id, String name, Boolean paid, Boolean requiresDocument, Double defaultAnnualQuota, Boolean active) {
        this.id = id;
        this.name = name;
        this.paid = paid;
        this.requiresDocument = requiresDocument;
        this.defaultAnnualQuota = defaultAnnualQuota;
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
