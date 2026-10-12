package com.lms.Leave_Management_System_Backend.security;

import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RoleBasedAccessAspectTest {

    private final RoleBasedAccessAspect aspect = new RoleBasedAccessAspect();

    static class Dummy {
        @RequireRole({"HR_ADMIN"})
        public void hrOnly() {
        }

        @RequireRole({"MANAGER", "HR_ADMIN"})
        public void managerOrHr() {
        }
    }

    private static RequireRole annotationOf(String method) throws Exception {
        return Dummy.class.getMethod(method).getAnnotation(RequireRole.class);
    }

    private static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user@lms.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("proceeds when the user has the required role")
    void allowsMatchingRole() throws Throwable {
        loginAs("HR_ADMIN");
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.proceed()).thenReturn("result");

        Object result = aspect.checkRole(jp, annotationOf("hrOnly"));

        assertThat(result).isEqualTo("result");
        verify(jp).proceed();
    }

    @Test
    @DisplayName("proceeds when the user has any one of several allowed roles")
    void allowsAnyOfSeveralRoles() throws Throwable {
        loginAs("MANAGER");
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

        aspect.checkRole(jp, annotationOf("managerOrHr"));

        verify(jp).proceed();
    }

    @Test
    @DisplayName("rejects a user with the wrong role")
    void rejectsWrongRole() throws Exception {
        loginAs("EMPLOYEE");
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.checkRole(jp, annotationOf("hrOnly")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not have required role");
        verifyNoInteractions(jp);
    }

    @Test
    @DisplayName("rejects an unauthenticated request")
    void rejectsUnauthenticated() throws Exception {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.checkRole(jp, annotationOf("hrOnly")))
                .isInstanceOf(SecurityException.class)
                .hasMessage("User not authenticated");
    }

    @Test
    @DisplayName("rejects an authentication object that is not authenticated")
    void rejectsNotAuthenticatedToken() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@lms.com", null)); // isAuthenticated() == false
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.checkRole(jp, annotationOf("hrOnly")))
                .isInstanceOf(SecurityException.class);
    }
}
