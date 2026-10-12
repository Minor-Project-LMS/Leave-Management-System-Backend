package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class ReportDistributionResponse {

    private Double total;
    private List<ReportDistributionItem> items;

    public ReportDistributionResponse() {
    }

    public ReportDistributionResponse(Double total, List<ReportDistributionItem> items) {
        this.total = total;
        this.items = items;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public List<ReportDistributionItem> getItems() {
        return items;
    }

    public void setItems(List<ReportDistributionItem> items) {
        this.items = items;
    }
}
