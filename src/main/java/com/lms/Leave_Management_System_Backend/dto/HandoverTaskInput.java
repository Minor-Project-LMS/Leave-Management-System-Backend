package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HandoverTaskInput {

    @NotBlank
    @Size(max = 300)
    private String description;

    // LOW | MEDIUM | HIGH — defaults to MEDIUM if omitted (see
    // LeaveRequestsController where these get saved).
    private String urgency;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }
}
