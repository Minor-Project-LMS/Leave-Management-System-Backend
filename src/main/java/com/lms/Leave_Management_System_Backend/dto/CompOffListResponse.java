package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class CompOffListResponse {
    private int page;
    private int limit;
    private long totalCount;
    private int totalPages;
    private List<CompOffRequestDto> data;

    public CompOffListResponse() {
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public List<CompOffRequestDto> getData() {
        return data;
    }

    public void setData(List<CompOffRequestDto> data) {
        this.data = data;
    }
}
