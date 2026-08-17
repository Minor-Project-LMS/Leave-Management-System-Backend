package com.lms.Leave_Management_System_Backend.security;

import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class RoleBasedAccessAspect {

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        String[] requiredRoles = requireRole.value();
        List<String> userRoles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .toList();

        boolean hasRequiredRole = Arrays.stream(requiredRoles)
                .anyMatch(userRoles::contains);

        if (!hasRequiredRole) {
            throw new SecurityException("User does not have required role. Required: " + 
                    Arrays.toString(requiredRoles) + ", User has: " + userRoles);
        }

        return joinPoint.proceed();
    }
}
