package com.lms.Leave_Management_System_Backend.dto;

public class ApprovalSlaSettingsDto {

    private int slaDays;
    // Nullable so a PATCH that only sends slaDays leaves these untouched.
    private Integer urgentWindowDays;
    private Integer escalationGraceDays;

    public int getSlaDays() {
        return slaDays;
    }

    public void setSlaDays(int slaDays) {
        this.slaDays = slaDays;
    }

    public Integer getUrgentWindowDays() {
        return urgentWindowDays;
    }

    public void setUrgentWindowDays(Integer urgentWindowDays) {
        this.urgentWindowDays = urgentWindowDays;
    }

    public Integer getEscalationGraceDays() {
        return escalationGraceDays;
    }

    public void setEscalationGraceDays(Integer escalationGraceDays) {
        this.escalationGraceDays = escalationGraceDays;
    }
}
