package com.lms.Leave_Management_System_Backend.dto;

// One slice of the "Leave Distribution by Type" donut.
public class ReportDistributionItem {

    private String label;
    private Double value;
    private String color;

    public ReportDistributionItem() {
    }

    public ReportDistributionItem(String label, Double value, String color) {
        this.label = label;
        this.value = value;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
