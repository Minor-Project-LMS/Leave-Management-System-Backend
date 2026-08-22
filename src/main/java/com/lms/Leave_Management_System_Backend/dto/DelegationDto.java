package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DelegationDto {
    private Integer delegationId;
    private UserDto delegator;
    private UserDto delegate;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private DelegationScope scope;
    private LocalDateTime createdAt;

    // Constructors
    public DelegationDto() {}

    // Getters and Setters
    public Integer getDelegationId() {
        return delegationId;
    }

    public void setDelegationId(Integer delegationId) {
        this.delegationId = delegationId;
    }

    public UserDto getDelegator() {
        return delegator;
    }

    public void setDelegator(UserDto delegator) {
        this.delegator = delegator;
    }

    public UserDto getDelegate() {
        return delegate;
    }

    public void setDelegate(UserDto delegate) {
        this.delegate = delegate;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public DelegationScope getScope() {
        return scope;
    }

    public void setScope(DelegationScope scope) {
        this.scope = scope;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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