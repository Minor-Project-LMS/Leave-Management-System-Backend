package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class PaginatedResponse<T> {
    private boolean success;
    private List<T> data;
    private PageResponse page;

    public PaginatedResponse() {
    }

    public PaginatedResponse(boolean success, List<T> data, PageResponse page) {
        this.success = success;
        this.data = data;
        this.page = page;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public PageResponse getPage() {
        return page;
    }

    public void setPage(PageResponse page) {
        this.page = page;
    }
}
