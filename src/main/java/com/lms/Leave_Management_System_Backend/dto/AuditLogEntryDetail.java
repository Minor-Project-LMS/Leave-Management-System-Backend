package com.lms.Leave_Management_System_Backend.dto;

import java.util.Map;

public class AuditLogEntryDetail extends AuditLogEntry {
    
    private Map<String, Object> beforeState;
    private Map<String, Object> afterState;

    // Getters and Setters
    public Map<String, Object> getBeforeState() {
        return beforeState;
    }

    public void setBeforeState(Map<String, Object> beforeState) {
        this.beforeState = beforeState;
    }

    public Map<String, Object> getAfterState() {
        return afterState;
    }

    public void setAfterState(Map<String, Object> afterState) {
        this.afterState = afterState;
    }
}