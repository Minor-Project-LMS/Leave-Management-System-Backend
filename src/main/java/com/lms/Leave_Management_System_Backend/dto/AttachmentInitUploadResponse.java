package com.lms.Leave_Management_System_Backend.dto;

import java.util.Map;

public class AttachmentInitUploadResponse {

    private Long attachmentId;
    private String uploadUrl;
    private String uploadMethod;
    private Map<String, String> requiredHeaders;
    private String storageKey;
    private Integer expiresInSeconds;

    public AttachmentInitUploadResponse() {
        this.uploadMethod = "PUT";
        this.expiresInSeconds = 300; // 5 minutes default
    }

    public AttachmentInitUploadResponse(Long attachmentId, String uploadUrl, String storageKey) {
        this();
        this.attachmentId = attachmentId;
        this.uploadUrl = uploadUrl;
        this.storageKey = storageKey;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getUploadMethod() {
        return uploadMethod;
    }

    public void setUploadMethod(String uploadMethod) {
        this.uploadMethod = uploadMethod;
    }

    public Map<String, String> getRequiredHeaders() {
        return requiredHeaders;
    }

    public void setRequiredHeaders(Map<String, String> requiredHeaders) {
        this.requiredHeaders = requiredHeaders;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Integer getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Integer expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
