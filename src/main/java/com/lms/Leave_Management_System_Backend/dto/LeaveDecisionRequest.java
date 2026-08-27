package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LeaveDecisionRequest {
    @NotNull
    private String decision; // APPROVED or REJECTED

    @Size(max = 2000)
    private String comments;

    public LeaveDecisionRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
