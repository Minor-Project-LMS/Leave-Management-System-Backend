package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class LeaveRequestDetailDto extends LeaveRequestDto {
    
    private String contactNumber;
    private String addressDuringLeave;
    private Integer handoverTo;
    private String handoverToName;
    private String handoverNotes;
    private UserDto employee;
    private List<LeaveLedgerSummaryDto> balanceAsOfRequestDate;
    private List<AttachmentDto> attachments;
    private List<LeaveApproval> approvals;
    private List<TeamImpact> teamImpact;

    // Getters and Setters
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

    public Integer getHandoverTo() {
        return handoverTo;
    }

    public void setHandoverTo(Integer handoverTo) {
        this.handoverTo = handoverTo;
    }

    public String getHandoverToName() {
        return handoverToName;
    }

    public void setHandoverToName(String handoverToName) {
        this.handoverToName = handoverToName;
    }

    public String getHandoverNotes() {
        return handoverNotes;
    }

    public void setHandoverNotes(String handoverNotes) {
        this.handoverNotes = handoverNotes;
    }

    public UserDto getEmployee() {
        return employee;
    }

    public void setEmployee(UserDto employee) {
        this.employee = employee;
    }

    public List<LeaveLedgerSummaryDto> getBalanceAsOfRequestDate() {
        return balanceAsOfRequestDate;
    }

    public void setBalanceAsOfRequestDate(List<LeaveLedgerSummaryDto> balanceAsOfRequestDate) {
        this.balanceAsOfRequestDate = balanceAsOfRequestDate;
    }

    public List<AttachmentDto> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentDto> attachments) {
        this.attachments = attachments;
    }

    public List<LeaveApproval> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<LeaveApproval> approvals) {
        this.approvals = approvals;
    }

    public List<TeamImpact> getTeamImpact() {
        return teamImpact;
    }

    public void setTeamImpact(List<TeamImpact> teamImpact) {
        this.teamImpact = teamImpact;
    }

    // Nested class for team impact
    public static class TeamImpact {
        private String description;
        private String backupPerson;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBackupPerson() {
            return backupPerson;
        }

        public void setBackupPerson(String backupPerson) {
            this.backupPerson = backupPerson;
        }
    }
}