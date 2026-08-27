package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class ApprovalInboxResponse {
    private int page;
    private int limit;
    private long totalCount;
    private int totalPages;
    private ApprovalCounts counts;
    private List<LeaveRequestDto> data;

    public ApprovalInboxResponse() {
    }

    public ApprovalInboxResponse(int page, int limit, long totalCount, int totalPages, ApprovalCounts counts, List<LeaveRequestDto> data) {
        this.page = page;
        this.limit = limit;
        this.totalCount = totalCount;
        this.totalPages = totalPages;
        this.counts = counts;
        this.data = data;
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

    public ApprovalCounts getCounts() {
        return counts;
    }

    public void setCounts(ApprovalCounts counts) {
        this.counts = counts;
    }

    public List<LeaveRequestDto> getData() {
        return data;
    }

    public void setData(List<LeaveRequestDto> data) {
        this.data = data;
    }

    public static class ApprovalCounts {
        private int all;
        private int pending;
        private int approved;
        private int rejected;

        public ApprovalCounts() {
        }

        public ApprovalCounts(int all, int pending, int approved, int rejected) {
            this.all = all;
            this.pending = pending;
            this.approved = approved;
            this.rejected = rejected;
        }

        public int getAll() {
            return all;
        }

        public void setAll(int all) {
            this.all = all;
        }

        public int getPending() {
            return pending;
        }

        public void setPending(int pending) {
            this.pending = pending;
        }

        public int getApproved() {
            return approved;
        }

        public void setApproved(int approved) {
            this.approved = approved;
        }

        public int getRejected() {
            return rejected;
        }

        public void setRejected(int rejected) {
            this.rejected = rejected;
        }
    }
}
