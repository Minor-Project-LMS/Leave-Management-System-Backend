package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ApiErrorResponse {
    private boolean success = false;
    private ErrorDetail error;
    private String path;
    private LocalDateTime timestamp;

    public ApiErrorResponse(String code, String message, String path) {
        this.error = new ErrorDetail(code, message);
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }

    public ApiErrorResponse(String code, String message, List<FieldError> fieldErrors, String path) {
        this.error = new ErrorDetail(code, message, fieldErrors);
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public static class ErrorDetail {
        private String code;
        private String message;
        private List<FieldError> fieldErrors;

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public ErrorDetail(String code, String message, List<FieldError> fieldErrors) {
            this.code = code;
            this.message = message;
            this.fieldErrors = fieldErrors;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<FieldError> getFieldErrors() {
            return fieldErrors;
        }

        public void setFieldErrors(List<FieldError> fieldErrors) {
            this.fieldErrors = fieldErrors;
        }
    }

    public static class FieldError {
        private String field;
        private String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}