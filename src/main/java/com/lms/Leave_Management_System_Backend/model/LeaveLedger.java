package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_ledger", 
    indexes = {
        @jakarta.persistence.Index(name = "idx_user_id", columnList = "user_id"),
        @jakarta.persistence.Index(name = "idx_category_id", columnList = "category_id"),
        @jakarta.persistence.Index(name = "idx_fiscal_year", columnList = "fiscal_year"),
        @jakarta.persistence.Index(name = "idx_user_category_year", columnList = "user_id, category_id, fiscal_year")
    }
)
public class LeaveLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private LeaveCategory category;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "accrued", nullable = false)
    private BigDecimal accrued = BigDecimal.ZERO;

    @Column(name = "used", nullable = false)
    private BigDecimal used = BigDecimal.ZERO;

    @Column(name = "encashed", nullable = false)
    private BigDecimal encashed = BigDecimal.ZERO;

    @Column(name = "carried_forward", nullable = false)
    private BigDecimal carriedForward = BigDecimal.ZERO;

    @Column(name = "closing_balance", nullable = false)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    // Additional fields for transaction tracking (not in original DB schema but used by controllers)
    @Column(name = "transaction_date")
    private java.time.LocalDate transactionDate;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "description")
    private String description;

    public LeaveLedger() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LeaveCategory getCategory() {
        return category;
    }

    public void setCategory(LeaveCategory category) {
        this.category = category;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getAccrued() {
        return accrued;
    }

    public void setAccrued(BigDecimal accrued) {
        this.accrued = accrued;
    }

    public BigDecimal getUsed() {
        return used;
    }

    public void setUsed(BigDecimal used) {
        this.used = used;
    }

    public BigDecimal getEncashed() {
        return encashed;
    }

    public void setEncashed(BigDecimal encashed) {
        this.encashed = encashed;
    }

    public BigDecimal getCarriedForward() {
        return carriedForward;
    }

    public void setCarriedForward(BigDecimal carriedForward) {
        this.carriedForward = carriedForward;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public java.time.LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(java.time.LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}