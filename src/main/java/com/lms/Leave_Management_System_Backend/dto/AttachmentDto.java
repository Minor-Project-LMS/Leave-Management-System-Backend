package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDateTime;

public class AttachmentDto {
    private Integer id;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private String downloadUrl;

    // Constructors
    public AttachmentDto() {}

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

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

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    // Backward compatibility
    public Integer getAttachmentId() {
        return id;
    }

    public void setAttachmentId(Integer attachmentId) {
        this.id = attachmentId;
    }

    public String getFileUrl() {
        return downloadUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.downloadUrl = fileUrl;
    }
}