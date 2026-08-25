package com.lms.Leave_Management_System_Backend.dto;

public class PageResponse<T> {
    private int page;
    private int limit;
    private long totalCount;
    private int totalPages;

    public PageResponse() {
    }

    public PageResponse(int page, int limit, long totalCount, int totalPages) {
        this.page = page;
        this.limit = limit;
        this.totalCount = totalCount;
        this.totalPages = totalPages;
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
}
