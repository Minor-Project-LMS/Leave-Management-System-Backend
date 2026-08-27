package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CompOffRequestDto {
    private Integer id;
    private String displayId;
    private Integer userId;
    private String employeeName;
    private LocalDate workedOn;
    private String reason;
    private Double daysCredited;
    private LocalDate expiryDate;
    private String status;
    private Integer approverId;
    private String approverName;
    private LocalDateTime createdAt;

    // Constructors
    public CompOffRequestDto() {}

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public LocalDate getWorkedOn() {
        return workedOn;
    }

    public void setWorkedOn(LocalDate workedOn) {
        this.workedOn = workedOn;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getDaysCredited() {
        return daysCredited;
    }

    public void setDaysCredited(Double daysCredited) {
        this.daysCredited = daysCredited;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getApproverId() {
        return approverId;
    }

    public void setApproverId(Integer approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}