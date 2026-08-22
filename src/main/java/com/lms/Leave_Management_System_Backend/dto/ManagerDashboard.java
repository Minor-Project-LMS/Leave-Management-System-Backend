package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;
import java.util.Map;

public class ManagerDashboard {
    
    private Map<String, Object> kpis;
    private List<Map<String, Object>> leaveTrend;
    private List<Map<String, Object>> leaveByType;
    private List<Map<String, Object>> teamLeaveByDepartment;
    private List<LeaveRequestDto> pendingApprovalsShortlist;
    private List<LeaveRequestDto> upcomingLeaves;

    public ManagerDashboard() {
    }

    public Map<String, Object> getKpis() {
        return kpis;
    }

    public void setKpis(Map<String, Object> kpis) {
        this.kpis = kpis;
    }

    public List<Map<String, Object>> getLeaveTrend() {
        return leaveTrend;
    }

    public void setLeaveTrend(List<Map<String, Object>> leaveTrend) {
        this.leaveTrend = leaveTrend;
    }

    public List<Map<String, Object>> getLeaveByType() {
        return leaveByType;
    }

    public void setLeaveByType(List<Map<String, Object>> leaveByType) {
        this.leaveByType = leaveByType;
    }

    public List<Map<String, Object>> getTeamLeaveByDepartment() {
        return teamLeaveByDepartment;
    }

    public void setTeamLeaveByDepartment(List<Map<String, Object>> teamLeaveByDepartment) {
        this.teamLeaveByDepartment = teamLeaveByDepartment;
    }

    public List<LeaveRequestDto> getPendingApprovalsShortlist() {
        return pendingApprovalsShortlist;
    }

    public void setPendingApprovalsShortlist(List<LeaveRequestDto> pendingApprovalsShortlist) {
        this.pendingApprovalsShortlist = pendingApprovalsShortlist;
    }

    public List<LeaveRequestDto> getUpcomingLeaves() {
        return upcomingLeaves;
    }

    public void setUpcomingLeaves(List<LeaveRequestDto> upcomingLeaves) {
        this.upcomingLeaves = upcomingLeaves;
    }
}