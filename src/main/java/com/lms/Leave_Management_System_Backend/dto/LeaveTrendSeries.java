package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class LeaveTrendSeries {
    private String category;
    private String color;
    private List<LeaveTrendPoint> points;

    public LeaveTrendSeries(String category, String color, List<LeaveTrendPoint> points) {
        this.category = category;
        this.color = color;
        this.points = points;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<LeaveTrendPoint> getPoints() {
        return points;
    }

    public void setPoints(List<LeaveTrendPoint> points) {
        this.points = points;
    }
}