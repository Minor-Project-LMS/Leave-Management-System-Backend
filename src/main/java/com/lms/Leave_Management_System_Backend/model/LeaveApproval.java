package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_approvals", 
    indexes = {
        @jakarta.persistence.Index(name = "idx_request_id", columnList = "request_id"),
        @jakarta.persistence.Index(name = "idx_approver_id", columnList = "approver_id"),
        @jakarta.persistence.Index(name = "idx_request_approver", columnList = "request_id, approver_id")
    }
)
public class LeaveApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private LeaveRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "decision", nullable = false)
    @Enumerated(EnumType.STRING)
    private Decision decision;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Column(name = "comments")
    private String comments;

    public enum Decision {
        APPROVED, REJECTED
    }

    public LeaveApproval() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LeaveRequest getRequest() {
        return request;
    }

    public void setRequest(LeaveRequest request) {
        this.request = request;
    }

    public User getApprover() {
        return approver;
    }

    public void setApprover(User approver) {
        this.approver = approver;
    }

    public Short getLevel() {
        return level;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
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