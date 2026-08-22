package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;
import java.util.Map;

public class HrDashboard {
    
    private Map<String, Object> kpis;
    private List<Map<String, Object>> departmentWiseCount;
    private Integer orgPendingApprovals;
    private Map<String, Object> notificationQueueHealth;

    public HrDashboard() {
    }

    public Map<String, Object> getKpis() {
        return kpis;
    }

    public void setKpis(Map<String, Object> kpis) {
        this.kpis = kpis;
    }

    public List<Map<String, Object>> getDepartmentWiseCount() {
        return departmentWiseCount;
    }

    public void setDepartmentWiseCount(List<Map<String, Object>> departmentWiseCount) {
        this.departmentWiseCount = departmentWiseCount;
    }

    public Integer getOrgPendingApprovals() {
        return orgPendingApprovals;
    }

    public void setOrgPendingApprovals(Integer orgPendingApprovals) {
        this.orgPendingApprovals = orgPendingApprovals;
    }

    public Map<String, Object> getNotificationQueueHealth() {
        return notificationQueueHealth;
    }

    public void setNotificationQueueHealth(Map<String, Object> notificationQueueHealth) {
        this.notificationQueueHealth = notificationQueueHealth;
    }
}