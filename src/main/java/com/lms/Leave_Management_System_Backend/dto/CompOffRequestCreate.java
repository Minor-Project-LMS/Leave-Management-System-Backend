package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class CompOffRequestCreate {
    @NotNull(message = "workedOn is required")
    private LocalDate workedOn;
    
    @NotBlank(message = "reason is required")
    @Size(max = 2000, message = "reason must be less than 2000 characters")
    private String reason;
    
    @NotNull(message = "hoursWorked is required")
    private Double hoursWorked;

    // Constructors
    public CompOffRequestCreate() {}

    // Getters and Setters
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

    public Double getHoursWorked() {
        return hoursWorked;
    }

    public void setHoursWorked(Double hoursWorked) {
        this.hoursWorked = hoursWorked;
    }
}