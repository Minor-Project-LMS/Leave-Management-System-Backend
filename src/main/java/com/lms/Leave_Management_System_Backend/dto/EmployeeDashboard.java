package com.lms.Leave_Management_System_Backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class EmployeeDashboard {
    
    private Map<String, Object> kpis;
    private List<Map<String, Object>> usageTrend;
    private List<Map<String, Object>> leaveDistribution;
    private List<HolidayDto> upcomingHolidays;
    private List<LeaveRequestDto> recentRequests;
    private List<Map<String, Object>> recentActivity;

    public EmployeeDashboard() {
    }

    public Map<String, Object> getKpis() {
        return kpis;
    }

    public void setKpis(Map<String, Object> kpis) {
        this.kpis = kpis;
    }

    public List<Map<String, Object>> getUsageTrend() {
        return usageTrend;
    }

    public void setUsageTrend(List<Map<String, Object>> usageTrend) {
        this.usageTrend = usageTrend;
    }

    public List<Map<String, Object>> getLeaveDistribution() {
        return leaveDistribution;
    }

    public void setLeaveDistribution(List<Map<String, Object>> leaveDistribution) {
        this.leaveDistribution = leaveDistribution;
    }

    public List<HolidayDto> getUpcomingHolidays() {
        return upcomingHolidays;
    }

    public void setUpcomingHolidays(List<HolidayDto> upcomingHolidays) {
        this.upcomingHolidays = upcomingHolidays;
    }

    public List<LeaveRequestDto> getRecentRequests() {
        return recentRequests;
    }

    public void setRecentRequests(List<LeaveRequestDto> recentRequests) {
        this.recentRequests = recentRequests;
    }

    public List<Map<String, Object>> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<Map<String, Object>> recentActivity) {
        this.recentActivity = recentActivity;
    }
}