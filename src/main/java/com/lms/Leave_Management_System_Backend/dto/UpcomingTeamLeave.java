package com.lms.Leave_Management_System_Backend.dto;

public class UpcomingTeamLeave {
    
    private String day;
    private String month;
    private String name;
    private String type;
    private String dateRange;

    // Constructor for simplified creation
    public UpcomingTeamLeave(String day, String month, String name, String type, String dateRange) {
        this.day = day;
        this.month = month;
        this.name = name;
        this.type = type;
        this.dateRange = dateRange;
    }

    // Default constructor
    public UpcomingTeamLeave() {}

    // Getters and Setters
    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }
}