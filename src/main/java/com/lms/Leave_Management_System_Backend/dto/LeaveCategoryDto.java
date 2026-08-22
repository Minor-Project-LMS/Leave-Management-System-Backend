package com.lms.Leave_Management_System_Backend.dto;

public class LeaveCategoryDto {
    private Integer id;
    private String categoryName;
    private String categoryCode;
    private String categoryType;
    private String applicableTo;
    private Boolean isPaid;
    private Boolean requiresDocument;
    private Double defaultAnnualQuota;
    private String status;
    private Boolean isSystemCategory;

    public LeaveCategoryDto() {
    }

    public LeaveCategoryDto(Integer id, String name, Boolean paid, Boolean requiresDocument, Double defaultAnnualQuota, Boolean active) {
        this.id = id;
        this.categoryName = name;
        this.isPaid = paid;
        this.requiresDocument = requiresDocument;
        this.defaultAnnualQuota = defaultAnnualQuota;
        this.status = active ? "ACTIVE" : "INACTIVE";
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(String categoryType) {
        this.categoryType = categoryType;
    }

    public String getApplicableTo() {
        return applicableTo;
    }

    public void setApplicableTo(String applicableTo) {
        this.applicableTo = applicableTo;
    }

    public Boolean getIsPaid() {
        return isPaid;
    }

    public void setIsPaid(Boolean isPaid) {
        this.isPaid = isPaid;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsSystemCategory() {
        return isSystemCategory;
    }

    public void setIsSystemCategory(Boolean isSystemCategory) {
        this.isSystemCategory = isSystemCategory;
    }

    // Backward compatibility
    public String getName() {
        return categoryName;
    }

    public void setName(String name) {
        this.categoryName = name;
    }

    public Boolean getPaid() {
        return isPaid;
    }

    public void setPaid(Boolean paid) {
        this.isPaid = paid;
    }

    public Boolean getActive() {
        return "ACTIVE".equals(status);
    }

    public void setActive(Boolean active) {
        this.status = active ? "ACTIVE" : "INACTIVE";
    }
}
