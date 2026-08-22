package com.lms.Leave_Management_System_Backend.dto;

public class LeaveTrendPoint {
    private String month;
    private Double days;

    public LeaveTrendPoint(String month, Double days) {
        this.month = month;
        this.days = days;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Double getDays() {
        return days;
    }

    public void setDays(Double days) {
        this.days = days;
    }
}