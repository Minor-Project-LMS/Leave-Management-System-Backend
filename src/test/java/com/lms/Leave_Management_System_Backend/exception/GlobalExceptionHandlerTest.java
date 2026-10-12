package com.lms.Leave_Management_System_Backend.exception;

import com.lms.Leave_Management_System_Backend.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/test");
    }

    private static void assertError(ResponseEntity<ErrorResponse> res, int status, String code) {
        assertThat(res.getStatusCode().value()).isEqualTo(status);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().isSuccess()).isFalse();
        assertThat(res.getBody().getError().getCode()).isEqualTo(code);
        assertThat(res.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    void badCredentials_is401() {
        assertError(handler.handleBadCredentials(new BadCredentialsException("x"), request), 401, "INVALID_CREDENTIALS");
    }

    @Test
    void accessDenied_is403() {
        assertError(handler.handleAccessDenied(new AccessDeniedException("x"), request), 403, "ACCESS_DENIED");
    }

    @Test
    void securityException_is401WithMessage() {
        ResponseEntity<ErrorResponse> res = handler.handleSecurityException(new SecurityException("not yours"), request);
        assertError(res, 401, "SECURITY_VIOLATION");
        assertThat(res.getBody().getError().getMessage()).isEqualTo("not yours");
    }

    @Test
    void resourceNotFound_is404() {
        ResponseEntity<ErrorResponse> res = handler.handleResourceNotFound(new ResourceNotFoundException("User", 7), request);
        assertError(res, 404, "RESOURCE_NOT_FOUND");
        assertThat(res.getBody().getError().getMessage()).isEqualTo("User not found with id: 7");
    }

    @Test
    void businessRule_is422() {
        assertError(handler.handleBusinessRule(new BusinessRuleException("rule"), request), 422, "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void conflict_is409() {
        assertError(handler.handleConflict(new ConflictException("dup"), request), 409, "CONFLICT");
    }

    @Test
    void illegalArgument_is400() {
        assertError(handler.handleIllegalArgument(new IllegalArgumentException("bad"), request), 400, "INVALID_ARGUMENT");
    }

    @Test
    void illegalState_is409() {
        assertError(handler.handleIllegalState(new IllegalStateException("state"), request), 409, "CONFLICT");
    }

    @Test
    void generic_is500AndHidesMessage() {
        ResponseEntity<ErrorResponse> res = handler.handleGeneric(new RuntimeException("db password leaked"), request);
        assertError(res, 500, "INTERNAL_SERVER_ERROR");
        assertThat(res.getBody().getError().getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("validation errors list every rejected field")
    void validation_is400WithFieldErrors() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "email", "bad", false, null, null, "Email must be valid"));
        MethodParameter param = new MethodParameter(Object.class.getMethod("toString"), -1);

        ResponseEntity<ErrorResponse> res = handler.handleValidation(
                new MethodArgumentNotValidException(param, binding), request);

        assertError(res, 400, "VALIDATION_ERROR");
        assertThat(res.getBody().getError().getFieldErrors()).hasSize(1);
        assertThat(res.getBody().getError().getFieldErrors().get(0).getField()).isEqualTo("email");
        assertThat(res.getBody().getError().getFieldErrors().get(0).getRejectedValue()).isEqualTo("bad");
    }

    @Test
    void typeMismatch_is400NamingTheParameter() throws Exception {
        MethodParameter param = new MethodParameter(Object.class.getMethod("toString"), -1);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Long.class, "requestId", param, null);

        ResponseEntity<ErrorResponse> res = handler.handleTypeMismatch(ex, request);

        assertError(res, 400, "INVALID_PARAMETER");
        assertThat(res.getBody().getError().getMessage()).isEqualTo("Invalid parameter: requestId");
    }
}
