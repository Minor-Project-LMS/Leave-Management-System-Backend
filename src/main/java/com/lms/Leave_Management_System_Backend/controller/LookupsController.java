package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.RoleDto;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class LookupsController {

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<RoleDto>> getRoles(Authentication authentication) {

        // Simplified implementation - would query actual roles from database
        List<RoleDto> roles = List.of(
                createRoleDto(1, "EMPLOYEE", "Regular employee with standard leave access"),
                createRoleDto(2, "MANAGER", "Manager with team approval and reporting capabilities"),
                createRoleDto(3, "HR_ADMIN", "HR administrator with full system access")
        );

        return ResponseEntity.ok(roles);
    }

    // Helper method
    private RoleDto createRoleDto(Integer roleId, String roleCode, String roleDescription) {
        RoleDto role = new RoleDto();
        role.setId(roleId);
        role.setRoleCode(roleCode);
        role.setRoleDescription(roleDescription);
        return role;
    }
}