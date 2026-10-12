package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Single-row table (id is always 1) holding org-wide working-week config.
// Separate from SystemSettings/SettingsController on purpose — that
// controller doesn't actually persist anything it's given (getSystemSettings
// returns hardcoded values and updateSystemSettings just echoes the request
// body back without saving it), and fixing that whole page is a bigger,
// separate piece of work. This gives the one setting that actually needs to
// affect leave-day calculations (calculateTotalDays in
// LeaveRequestsController) a real, working place to live.
@Entity
@Table(name = "work_week_settings")
public class WorkWeekSettings {

    @Id
    private Integer id = 1;

    @Column(name = "saturday_working", nullable = false)
    private boolean saturdayWorking = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isSaturdayWorking() {
        return saturdayWorking;
    }

    public void setSaturdayWorking(boolean saturdayWorking) {
        this.saturdayWorking = saturdayWorking;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
