package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;
import java.util.List;

public class CompOffRequestCreate {
    private LocalDate workedOn;
    private String reason;
    private Double hoursWorked;
    private Double daysCredited;
    private List<Integer> attachmentIds;

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

    public Double getDaysCredited() {
        return daysCredited;
    }

    public void setDaysCredited(Double daysCredited) {
        this.daysCredited = daysCredited;
    }

    public List<Integer> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Integer> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }
}