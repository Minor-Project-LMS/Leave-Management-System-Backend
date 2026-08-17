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
    private String name;

    @Column(name = "is_paid", nullable = false)
    private boolean paid;

    @Column(name = "requires_document", nullable = false)
    private boolean requiresDocument;

    @Column(name = "default_annual_quota", nullable = false)
    private Double defaultAnnualQuota;

    public LeaveCategory() {
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

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
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
}
