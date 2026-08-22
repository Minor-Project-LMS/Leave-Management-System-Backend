package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;
import java.util.List;

public class DelegationRequest {
    private Integer delegateId;
    private LocalDate startDate;
    private LocalDate endDate;
    private DelegationScope scope;

    // Constructors
    public DelegationRequest() {}

    // Getters and Setters
    public Integer getDelegateId() {
        return delegateId;
    }

    public void setDelegateId(Integer delegateId) {
        this.delegateId = delegateId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public DelegationScope getScope() {
        return scope;
    }

    public void setScope(DelegationScope scope) {
        this.scope = scope;
    }

    public static class DelegationScope {
        private Boolean allTypes;
        private List<Integer> departmentIds;
        private List<Integer> categoryIds;

        // Getters and Setters
        public Boolean getAllTypes() {
            return allTypes;
        }

        public void setAllTypes(Boolean allTypes) {
            this.allTypes = allTypes;
        }

        public List<Integer> getDepartmentIds() {
            return departmentIds;
        }

        public void setDepartmentIds(List<Integer> departmentIds) {
            this.departmentIds = departmentIds;
        }

        public List<Integer> getCategoryIds() {
            return categoryIds;
        }

        public void setCategoryIds(List<Integer> categoryIds) {
            this.categoryIds = categoryIds;
        }
    }
}