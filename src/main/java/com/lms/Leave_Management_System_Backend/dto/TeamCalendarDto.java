package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class TeamCalendarDto {
    private String month;
    private List<DayEntry> days;
    private List<CategorySummary> summaryByCategory;

    // Constructors
    public TeamCalendarDto() {}

    // Getters and Setters
    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public List<DayEntry> getDays() {
        return days;
    }

    public void setDays(List<DayEntry> days) {
        this.days = days;
    }

    public List<CategorySummary> getSummaryByCategory() {
        return summaryByCategory;
    }

    public void setSummaryByCategory(List<CategorySummary> summaryByCategory) {
        this.summaryByCategory = summaryByCategory;
    }

    // Inner classes
    public static class DayEntry {
        private String date;
        private List<EmployeeOnLeave> onLeave;

        // Getters and Setters
        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public List<EmployeeOnLeave> getOnLeave() {
            return onLeave;
        }

        public void setOnLeave(List<EmployeeOnLeave> onLeave) {
            this.onLeave = onLeave;
        }
    }

    public static class EmployeeOnLeave {
        private Long userId;
        private String fullName;
        private String avatarUrl;
        private String categoryName;

        // Getters and Setters
        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }
    }

    public static class CategorySummary {
        private String categoryName;
        private Double totalDays;

        // Getters and Setters
        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public Double getTotalDays() {
            return totalDays;
        }

        public void setTotalDays(Double totalDays) {
            this.totalDays = totalDays;
        }
    }
}