package com.lms.Leave_Management_System_Backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ErrorResponse {
    private boolean success;
    private ErrorDetail error;
    private String path;
    private String timestamp;

    public ErrorResponse() {
        this.success = false;
        this.timestamp = Instant.now().toString();
    }

    public ErrorResponse(String code, String message, String path) {
        this();
        this.error = new ErrorDetail(code, message);
        this.path = path;
    }

    public ErrorResponse(String code, String message, List<FieldError> fieldErrors, String path) {
        this();
        this.error = new ErrorDetail(code, message, fieldErrors);
        this.path = path;
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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public static class ErrorDetail {
        private String code;
        private String message;
        private List<FieldError> fieldErrors;

        public ErrorDetail() {
        }

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
        private Object rejectedValue;

        public FieldError() {
        }

        public FieldError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
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

        public Object getRejectedValue() {
            return rejectedValue;
        }

        public void setRejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
        }
    }
}
