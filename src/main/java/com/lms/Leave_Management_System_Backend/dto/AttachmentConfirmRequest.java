package com.lms.Leave_Management_System_Backend.dto;

public class AttachmentConfirmRequest {

    // Optional checksum for integrity verification
    private String checksumSha256;

    public AttachmentConfirmRequest() {
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }
}
