package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class LeaveDonationRequest {

    @NotNull
    private Long recipientId;

    @NotNull
    private Integer categoryId;

    @NotNull
    @DecimalMin(value = "0.5", message = "Must donate at least 0.5 day")
    private BigDecimal days;

    @Size(max = 500)
    private String reason;

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getDays() {
        return days;
    }

    public void setDays(BigDecimal days) {
        this.days = days;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
