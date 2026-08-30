package com.lms.Leave_Management_System_Backend.dto;

import java.net.URL;
import java.time.LocalDateTime;

public class AttachmentUploadUrl {
    
    private Long attachmentId;
    private URL uploadUrl;
    private String storageKey;
    private LocalDateTime expiresAt;
    
    // Getters and Setters
    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public URL getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(URL uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
