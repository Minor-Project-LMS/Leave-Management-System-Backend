package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotBlank;

public class LeaveDonationDecisionRequest {

    @NotBlank
    private String decision; // APPROVED | REJECTED

    private String comments;

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
