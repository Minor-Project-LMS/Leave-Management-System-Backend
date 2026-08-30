package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AttachmentUploadUrlInput {
    
    @NotNull
    @Size(max = 255)
    private String fileName;
    
    @NotNull
    @Size(max = 100)
    private String contentType;
    
    @NotNull
    private Long sizeBytes;
    
    @NotNull
    @Size(max = 30)
    private String entityType;
    
    private Long entityId;
    
    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }
}
