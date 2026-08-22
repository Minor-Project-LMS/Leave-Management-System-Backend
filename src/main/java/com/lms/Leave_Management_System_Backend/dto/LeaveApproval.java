package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDateTime;

public class LeaveApproval {
    
    private Integer id;
    private Integer requestId;
    private Integer approverId;
    private String approverName;
    private Integer actingAsDelegateFor;
    private Integer level;
    private String decision;
    private LocalDateTime decidedAt;
    private String comments;

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public void setRequestId(Integer requestId) {
        this.requestId = requestId;
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

    public Integer getActingAsDelegateFor() {
        return actingAsDelegateFor;
    }

    public void setActingAsDelegateFor(Integer actingAsDelegateFor) {
        this.actingAsDelegateFor = actingAsDelegateFor;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}