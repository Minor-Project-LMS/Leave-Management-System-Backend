package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;

public class HolidayDto {
    private Integer id;
    private String name;
    private LocalDate date;
    private Integer departmentId;
    private String departmentName;
    private boolean restricted;

    public HolidayDto() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }
    
    // Deprecated - kept for backward compatibility if needed
    @Deprecated
    public boolean isRecurring() {
        return false;
    }
    
    @Deprecated
    public void setRecurring(boolean recurring) {
        // No-op since we changed to restricted
    }
}
