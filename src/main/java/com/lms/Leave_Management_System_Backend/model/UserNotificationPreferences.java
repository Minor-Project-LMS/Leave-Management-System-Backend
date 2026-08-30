package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "leave_request_updates", nullable = false)
    private Boolean leaveRequestUpdates = true;

    @Column(name = "approval_notifications", nullable = false)
    private Boolean approvalNotifications = true;

    @Column(name = "comp_off_updates", nullable = false)
    private Boolean compOffUpdates = true;

    @Column(name = "policy_updates", nullable = false)
    private Boolean policyUpdates = false;

    @Column(name = "system_notifications", nullable = false)
    private Boolean systemNotifications = true;

    @Column(name = "holiday_reminders", nullable = false)
    private Boolean holidayReminders = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserNotificationPreferences() {
    }

    public UserNotificationPreferences(User user) {
        this.user = user;
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

    public Boolean getLeaveRequestUpdates() {
        return leaveRequestUpdates;
    }

    public void setLeaveRequestUpdates(Boolean leaveRequestUpdates) {
        this.leaveRequestUpdates = leaveRequestUpdates;
    }

    public Boolean getApprovalNotifications() {
        return approvalNotifications;
    }

    public void setApprovalNotifications(Boolean approvalNotifications) {
        this.approvalNotifications = approvalNotifications;
    }

    public Boolean getCompOffUpdates() {
        return compOffUpdates;
    }

    public void setCompOffUpdates(Boolean compOffUpdates) {
        this.compOffUpdates = compOffUpdates;
    }

    public Boolean getPolicyUpdates() {
        return policyUpdates;
    }

    public void setPolicyUpdates(Boolean policyUpdates) {
        this.policyUpdates = policyUpdates;
    }

    public Boolean getSystemNotifications() {
        return systemNotifications;
    }

    public void setSystemNotifications(Boolean systemNotifications) {
        this.systemNotifications = systemNotifications;
    }

    public Boolean getHolidayReminders() {
        return holidayReminders;
    }

    public void setHolidayReminders(Boolean holidayReminders) {
        this.holidayReminders = holidayReminders;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}