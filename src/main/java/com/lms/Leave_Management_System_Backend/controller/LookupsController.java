package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.Role;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.RoleRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class LookupsController {

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;

    public LookupsController(DepartmentRepository departmentRepository, RoleRepository roleRepository) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
    }

    @GetMapping("/departments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> listDepartments() {
        List<Department> departments = departmentRepository.findAll();
        List<DepartmentDto> departmentDtos = departments.stream()
                .map(this::toDepartmentDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>(true, departmentDtos));
    }

    @GetMapping("/roles")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<ApiResponse<List<RoleDto>>> listRoles() {
        List<Role> roles = roleRepository.findAll();
        List<RoleDto> roleDtos = roles.stream()
                .map(this::toRoleDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>(true, roleDtos));
    }

    private DepartmentDto toDepartmentDto(Department department) {
        DepartmentDto dto = new DepartmentDto(department.getId(), department.getName());
        if (department.getDepartmentHead() != null) {
            dto.setDepartmentHeadId(department.getDepartmentHead().getId());
            dto.setDepartmentHeadName(department.getDepartmentHead().getName());
        }
        return dto;
    }

    private RoleDto toRoleDto(Role role) {
        return new RoleDto(role.getId(), role.getRoleCode(), role.getRoleDescription());
    }
}
