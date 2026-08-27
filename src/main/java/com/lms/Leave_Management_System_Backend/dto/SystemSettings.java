package com.lms.Leave_Management_System_Backend.dto;

import java.util.Map;

public class SystemSettings {
    
    private Map<String, Object> general;
    private Map<String, Object> leaveSettings;
    private Map<String, Object> approvalWorkflow;
    private Map<String, Object> systemPreferences;
    private Map<String, Object> notifications;
    private Map<String, Object> security;
    private Map<String, Object> integrations;
    private Map<String, Object> systemInfo;

    // Getters and Setters
    public Map<String, Object> getGeneral() {
        return general;
    }

    public void setGeneral(Map<String, Object> general) {
        this.general = general;
    }

    public Map<String, Object> getLeaveSettings() {
        return leaveSettings;
    }

    public void setLeaveSettings(Map<String, Object> leaveSettings) {
        this.leaveSettings = leaveSettings;
    }

    public Map<String, Object> getApprovalWorkflow() {
        return approvalWorkflow;
    }

    public void setApprovalWorkflow(Map<String, Object> approvalWorkflow) {
        this.approvalWorkflow = approvalWorkflow;
    }

    public Map<String, Object> getSystemPreferences() {
        return systemPreferences;
    }

    public void setSystemPreferences(Map<String, Object> systemPreferences) {
        this.systemPreferences = systemPreferences;
    }

    public Map<String, Object> getNotifications() {
        return notifications;
    }

    public void setNotifications(Map<String, Object> notifications) {
        this.notifications = notifications;
    }

    public Map<String, Object> getSecurity() {
        return security;
    }

    public void setSecurity(Map<String, Object> security) {
        this.security = security;
    }

    public Map<String, Object> getIntegrations() {
        return integrations;
    }

    public void setIntegrations(Map<String, Object> integrations) {
        this.integrations = integrations;
    }

    public Map<String, Object> getSystemInfo() {
        return systemInfo;
    }

    public void setSystemInfo(Map<String, Object> systemInfo) {
        this.systemInfo = systemInfo;
    }
}