package com.lms.Leave_Management_System_Backend.dto;

public class NotificationPreferences {
    
    private Boolean leaveRequestUpdates;
    private Boolean approvalNotifications;
    private Boolean compOffUpdates;
    private Boolean policyUpdates;
    private Boolean systemNotifications;
    private Boolean holidayReminders;

    // Getters and Setters
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
}