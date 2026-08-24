package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "leave_categories")
public class LeaveCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer id;

    @Column(name = "category_name", nullable = false, unique = true)
    private String categoryName;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid;

    @Column(name = "requires_document", nullable = false)
    private boolean requiresDocument;

    @Column(name = "default_annual_quota", nullable = false)
    private Double defaultAnnualQuota;

    @Column(name = "category_code")
    private String categoryCode;

    @Column(name = "category_type")
    private String categoryType;

    @Column(name = "applicable_to")
    private String applicableTo = "ALL_EMPLOYEES";

    @Column(name = "status")
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    public LeaveCategory() {
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

    // For backward compatibility
    public String getName() {
        return categoryName;
    }

    public void setName(String name) {
        this.categoryName = name;
    }

    public boolean isPaid() {
        return isPaid;
    }

    public void setPaid(boolean isPaid) {
        this.isPaid = isPaid;
    }

    public boolean isRequiresDocument() {
        return requiresDocument;
    }

    public void setRequiresDocument(boolean requiresDocument) {
        this.requiresDocument = requiresDocument;
    }

    public Double getDefaultAnnualQuota() {
        return defaultAnnualQuota;
    }

    public void setDefaultAnnualQuota(Double defaultAnnualQuota) {
        this.defaultAnnualQuota = defaultAnnualQuota;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
}
