package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class LeaveRequestCreate {
    @NotNull
    private Integer categoryId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    private String sessionType = "FULL_DAY";

    @NotNull
    @Size(max = 2000)
    private String reason;

    private String contactNumber;
    private String addressDuringLeave;
    private Long handoverTo;
    private String handoverNotes;
    private String status = "PENDING_L1";
    private List<Integer> attachmentIds;

    // totalDays is calculated by server, not required from client
    private BigDecimal totalDays;

    public LeaveRequestCreate() {
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
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

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getAddressDuringLeave() {
        return addressDuringLeave;
    }

    public void setAddressDuringLeave(String addressDuringLeave) {
        this.addressDuringLeave = addressDuringLeave;
    }

    public Long getHandoverTo() {
        return handoverTo;
    }

    public void setHandoverTo(Long handoverTo) {
        this.handoverTo = handoverTo;
    }

    public String getHandoverNotes() {
        return handoverNotes;
    }

    public void setHandoverNotes(String handoverNotes) {
        this.handoverNotes = handoverNotes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Integer> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Integer> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public BigDecimal getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(BigDecimal totalDays) {
        this.totalDays = totalDays;
    }
}
