package com.lms.Leave_Management_System_Backend.dto;

import java.util.List;

public class CompOffRequestDetailDto extends CompOffRequestDto {
    private List<LeaveRequestDto> linkedLeaveRequests;
    private List<AttachmentDto> attachments;

    public CompOffRequestDetailDto() {
        super();
    }

    public List<LeaveRequestDto> getLinkedLeaveRequests() {
        return linkedLeaveRequests;
    }

    public void setLinkedLeaveRequests(List<LeaveRequestDto> linkedLeaveRequests) {
        this.linkedLeaveRequests = linkedLeaveRequests;
    }

    public List<AttachmentDto> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentDto> attachments) {
        this.attachments = attachments;
    }
}
