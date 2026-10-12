package com.lms.Leave_Management_System_Backend.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lms.Leave_Management_System_Backend.exception.GlobalExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/**
 * Shared helpers for controller tests.
 *
 * Controllers run in a standalone MockMvc (no Spring context, database,
 * Redis or S3), wired to the real GlobalExceptionHandler so HTTP status
 * codes match production. The @RequireRole aspect is NOT applied in
 * standalone mode; role enforcement is covered by RoleBasedAccessAspectTest
 * and RequireRoleEnforcementTest.
 */
public final class MvcTestSupport {

    /**
     * Request-body serializer. NON_NULL matters: several DTOs have alias
     * setters (e.g. DepartmentDto.setName -> departmentName), and sending
     * "name": null would overwrite the real value during deserialization.
     */
    public static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private MvcTestSupport() {
    }

    public static MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** Authentication principal shaped exactly like the one JwtAuthenticationFilter creates. */
    public static Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    public static String json(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
