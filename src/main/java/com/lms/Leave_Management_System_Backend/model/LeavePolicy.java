package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "leave_policies")
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private LeaveCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "annual_quota", nullable = false)
    private BigDecimal annualQuota;

    @Column(name = "max_carry_forward", nullable = false)
    private BigDecimal maxCarryForward = BigDecimal.ZERO;

    @Column(name = "min_notice_days", nullable = false)
    private Integer minNoticeDays = 0;

    @Column(name = "max_consecutive_days", nullable = false)
    private Integer maxConsecutiveDays = 0;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "policy_name")
    private String policyName;

    @Column(name = "policy_code")
    private String policyCode;

    @Column(name = "accrual_frequency")
    private String accrualFrequency = "ANNUAL";

    @Column(name = "status")
    private String status = "DRAFT";

    public LeavePolicy() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LeaveCategory getCategory() {
        return category;
    }

    public void setCategory(LeaveCategory category) {
        this.category = category;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public BigDecimal getAnnualQuota() {
        return annualQuota;
    }

    public void setAnnualQuota(BigDecimal annualQuota) {
        this.annualQuota = annualQuota;
    }

    public BigDecimal getMaxCarryForward() {
        return maxCarryForward;
    }

    public void setMaxCarryForward(BigDecimal maxCarryForward) {
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

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
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

    public String getAccrualFrequency() {
        return accrualFrequency;
    }

    public void setAccrualFrequency(String accrualFrequency) {
        this.accrualFrequency = accrualFrequency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}