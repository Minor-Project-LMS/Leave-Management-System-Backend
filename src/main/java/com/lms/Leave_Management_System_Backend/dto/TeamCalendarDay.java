package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;
import java.util.List;

public class TeamCalendarDay {
    private LocalDate date;
    private List<TeamCalendarEntry> entries;

    // Constructors
    public TeamCalendarDay() {}

    public TeamCalendarDay(LocalDate date, List<TeamCalendarEntry> entries) {
        this.date = date;
        this.entries = entries;
    }

    // Getters and Setters
    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<TeamCalendarEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<TeamCalendarEntry> entries) {
        this.entries = entries;
    }
}