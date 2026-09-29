package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.RoleDto;
import com.lms.Leave_Management_System_Backend.model.Role;
import com.lms.Leave_Management_System_Backend.repository.RoleRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/roles")
public class LookupsController {

    private final RoleRepository roleRepository;

    public LookupsController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<RoleDto>> getRoles(Authentication authentication) {

        List<RoleDto> roles = roleRepository.findAll().stream()
                .map(this::toRoleDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(roles);
    }

    private RoleDto toRoleDto(Role role) {
        return new RoleDto(role.getId(), role.getRoleCode(), role.getRoleDescription());
    }
}