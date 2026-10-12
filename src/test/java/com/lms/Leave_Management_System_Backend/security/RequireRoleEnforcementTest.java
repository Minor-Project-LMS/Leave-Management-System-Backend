package com.lms.Leave_Management_System_Backend.security;

import com.lms.Leave_Management_System_Backend.controller.SettingsController;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves @RequireRole really guards a controller once the aspect is woven
 * in (standalone MockMvc tests bypass the aspect, so this closes that gap).
 */
class RequireRoleEnforcementTest {

    private SettingsController proxiedController;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SettingsController());
        factory.setProxyTargetClass(true);
        factory.addAspect(new RoleBasedAccessAspect());
        proxiedController = factory.getProxy();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone@lms.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    @DisplayName("HR_ADMIN can read system settings")
    void hrAllowed() {
        loginAs("HR_ADMIN");
        assertThat(proxiedController.getSystemSettings(null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("EMPLOYEE is blocked from HR-only system settings")
    void employeeBlocked() {
        loginAs("EMPLOYEE");
        assertThatThrownBy(() -> proxiedController.getSystemSettings(null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("MANAGER is blocked from HR-only system settings")
    void managerBlocked() {
        loginAs("MANAGER");
        assertThatThrownBy(() -> proxiedController.updateSystemSettings(null, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("anonymous caller is blocked")
    void anonymousBlocked() {
        assertThatThrownBy(() -> proxiedController.getSystemSettings(null))
                .isInstanceOf(SecurityException.class)
                .hasMessage("User not authenticated");
    }
}
