package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;

public class CompOffRequestDto {
    private Integer compId;
    private UserDto employee;
    private LocalDate workedOn;
    private String reason;
    private Double daysCredited;
    private LocalDate expiryDate;
    private String status;
    private UserDto approver;
    private LocalDate createdAt;

    // Constructors
    public CompOffRequestDto() {}

    // Getters and Setters
    public Integer getCompId() {
        return compId;
    }

    public void setCompId(Integer compId) {
        this.compId = compId;
    }

    public UserDto getEmployee() {
        return employee;
    }

    public void setEmployee(UserDto employee) {
        this.employee = employee;
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

    public UserDto getApprover() {
        return approver;
    }

    public void setApprover(UserDto approver) {
        this.approver = approver;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }
}