package com.lms.Leave_Management_System_Backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LeaveRequestDto {
    private Long id;
    private Long userId;
    private String userName;
    private Integer categoryId;
    private String categoryName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sessionType;
    private BigDecimal totalDays;
    private BigDecimal lopDays;
    // Self-checklist the applicant adds when applying — visible to both the
    // employee (who checks items off) and the manager (read-only, informs
    // their approval decision but never blocks it).
    private List<HandoverTaskDto> handoverTasks;
    private boolean escalated;
    private LocalDateTime escalatedAt;
    private String originalApproverName;
    // Set once HR has notified the approver about this request (the step
    // before escalation) — the frontend pins these to the top and shows
    // them as a dashboard popup.
    private boolean hrNotified;
    private LocalDateTime hrNotifiedAt;
    private String hrNotifiedByName;
    private String hrNotificationMessage;
    // Leave starts within the urgent window, so it can be escalated.
    private boolean urgent;
    private String reason;
    private String status;
    private Long currentApproverId;
    private String currentApproverName;
    private String currentApproverAvatarUrl;
    private String userAvatarUrl;
    private Integer compOffRequestId;
    private LocalDateTime appliedAt;

    public LeaveRequestDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public BigDecimal getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(BigDecimal totalDays) {
        this.totalDays = totalDays;
    }

    public BigDecimal getLopDays() {
        return lopDays;
    }

    public void setLopDays(BigDecimal lopDays) {
        this.lopDays = lopDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentApproverId() {
        return currentApproverId;
    }

    public void setCurrentApproverId(Long currentApproverId) {
        this.currentApproverId = currentApproverId;
    }

    public String getCurrentApproverName() {
        return currentApproverName;
    }

    public void setCurrentApproverName(String currentApproverName) {
        this.currentApproverName = currentApproverName;
    }

    public String getCurrentApproverAvatarUrl() {
        return currentApproverAvatarUrl;
    }

    public void setCurrentApproverAvatarUrl(String currentApproverAvatarUrl) {
        this.currentApproverAvatarUrl = currentApproverAvatarUrl;
    }

    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }

    public void setUserAvatarUrl(String userAvatarUrl) {
        this.userAvatarUrl = userAvatarUrl;
    }

    public Integer getCompOffRequestId() {
        return compOffRequestId;
    }

    public void setCompOffRequestId(Integer compOffRequestId) {
        this.compOffRequestId = compOffRequestId;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public List<HandoverTaskDto> getHandoverTasks() {
        return handoverTasks;
    }

    public void setHandoverTasks(List<HandoverTaskDto> handoverTasks) {
        this.handoverTasks = handoverTasks;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public void setEscalated(boolean escalated) {
        this.escalated = escalated;
    }

    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(LocalDateTime escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public String getOriginalApproverName() {
        return originalApproverName;
    }

    public void setOriginalApproverName(String originalApproverName) {
        this.originalApproverName = originalApproverName;
    }

    public boolean isHrNotified() {
        return hrNotified;
    }

    public void setHrNotified(boolean hrNotified) {
        this.hrNotified = hrNotified;
    }

    public LocalDateTime getHrNotifiedAt() {
        return hrNotifiedAt;
    }

    public void setHrNotifiedAt(LocalDateTime hrNotifiedAt) {
        this.hrNotifiedAt = hrNotifiedAt;
    }

    public String getHrNotifiedByName() {
        return hrNotifiedByName;
    }

    public void setHrNotifiedByName(String hrNotifiedByName) {
        this.hrNotifiedByName = hrNotifiedByName;
    }

    public String getHrNotificationMessage() {
        return hrNotificationMessage;
    }

    public void setHrNotificationMessage(String hrNotificationMessage) {
        this.hrNotificationMessage = hrNotificationMessage;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }
}
