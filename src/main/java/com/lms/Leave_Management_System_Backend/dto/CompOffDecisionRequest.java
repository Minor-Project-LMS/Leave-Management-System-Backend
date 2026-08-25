package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompOffDecisionRequest {
    @NotBlank(message = "decision is required")
    private String decision;
    
    @Size(max = 2000, message = "comments must be less than 2000 characters")
    private String comments;

    public CompOffDecisionRequest() {
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
