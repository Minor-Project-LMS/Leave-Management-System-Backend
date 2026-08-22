package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", 
    indexes = {
        @jakarta.persistence.Index(name = "idx_user_id", columnList = "user_id"),
        @jakarta.persistence.Index(name = "idx_category_id", columnList = "category_id"),
        @jakarta.persistence.Index(name = "idx_current_approver_id", columnList = "current_approver_id"),
        @jakarta.persistence.Index(name = "idx_status", columnList = "status"),
        @jakarta.persistence.Index(name = "idx_start_date", columnList = "start_date"),
        @jakarta.persistence.Index(name = "idx_end_date", columnList = "end_date"),
        @jakarta.persistence.Index(name = "idx_user_status", columnList = "user_id, status")
    }
)
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private LeaveCategory category;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "session_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionType sessionType = SessionType.FULL_DAY;

    @Column(name = "total_days", nullable = false)
    private BigDecimal totalDays;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_approver_id")
    private User currentApprover;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    public enum SessionType {
        FULL_DAY, FIRST_HALF, SECOND_HALF
    }

    public enum RequestStatus {
        DRAFT, PENDING_L1, PENDING_L2, APPROVED, REJECTED, CANCELLED, WITHDRAWN
    }

    public LeaveRequest() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LeaveCategory getCategory() {
        return category;
    }

    public void setCategory(LeaveCategory category) {
        this.category = category;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public BigDecimal getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(BigDecimal totalDays) {
        this.totalDays = totalDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public User getCurrentApprover() {
        return currentApprover;
    }

    public void setCurrentApprover(User currentApprover) {
        this.currentApprover = currentApprover;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}
