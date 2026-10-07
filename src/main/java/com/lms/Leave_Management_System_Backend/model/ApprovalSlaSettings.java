package com.lms.Leave_Management_System_Backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Single-row table (id is always 1) holding how many days a leave request
// can sit pending before LeaveEscalationService auto-reassigns it to HR.
// Same pattern as WorkWeekSettings — its own small table rather than folded
// into SettingsController/SystemSettings, since that controller doesn't
// actually persist anything given to it.
@Entity
@Table(name = "approval_sla_settings")
public class ApprovalSlaSettings {

    @Id
    private Integer id = 1;

    @Column(name = "sla_days", nullable = false)
    private int slaDays = 3;

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

    public int getSlaDays() {
        return slaDays;
    }

    public void setSlaDays(int slaDays) {
        this.slaDays = slaDays;
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
