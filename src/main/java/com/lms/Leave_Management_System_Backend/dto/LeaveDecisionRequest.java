package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotBlank;

public class LeaveDecisionRequest {
    private String decision; // APPROVE or REJECT
    @NotBlank(message = "Comment is required for rejection")
    private String comment;

    public LeaveDecisionRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
