package com.lms.Leave_Management_System_Backend.dto;

public class CommentRequest {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // Backward compatibility
    public String getComment() {
        return message;
    }

    public void setComment(String comment) {
        this.message = comment;
    }
}