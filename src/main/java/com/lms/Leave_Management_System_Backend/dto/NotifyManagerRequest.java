package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.Size;

// Body for HR's "notify manager" action. message is optional — a default
// reminder is used when it's left blank.
public class NotifyManagerRequest {

    @Size(max = 500)
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
