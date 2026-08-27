package com.lms.Leave_Management_System_Backend.dto;

public class LeaveLedgerSummaryDto {
    private Integer categoryId;
    private String categoryName;
    private Integer fiscalYear;
    private Double openingBalance;
    private Double accrued;
    private Double used;
    private Double encashed;
    private Double carriedForward;
    private Double closingBalance;
    private Double availableBalance;

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public Double getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(Double openingBalance) {
        this.openingBalance = openingBalance;
    }

    public Double getAccrued() {
        return accrued;
    }

    public void setAccrued(Double accrued) {
        this.accrued = accrued;
    }

    public Double getUsed() {
        return used;
    }

    public void setUsed(Double used) {
        this.used = used;
    }

    public Double getEncashed() {
        return encashed;
    }

    public void setEncashed(Double encashed) {
        this.encashed = encashed;
    }

    public Double getCarriedForward() {
        return carriedForward;
    }

    public void setCarriedForward(Double carriedForward) {
        this.carriedForward = carriedForward;
    }

    public Double getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(Double closingBalance) {
        this.closingBalance = closingBalance;
    }

    public Double getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(Double availableBalance) {
        this.availableBalance = availableBalance;
    }
}