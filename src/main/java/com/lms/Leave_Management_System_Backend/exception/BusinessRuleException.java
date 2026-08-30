package com.lms.Leave_Management_System_Backend.exception;

public class BusinessRuleException extends RuntimeException {
    private String errorCode;

    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
