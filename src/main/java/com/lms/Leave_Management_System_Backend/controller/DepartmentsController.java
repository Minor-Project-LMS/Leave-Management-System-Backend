package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentsController {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DepartmentsController(DepartmentRepository departmentRepository, UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<DepartmentDto>> listDepartments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        
        // Contract uses 1-based page numbers, Spring uses 0-based
        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<Department> departments = departmentRepository.findAll(pageable);

        List<DepartmentDto> departmentDtos = departments.getContent().stream()
                .map(this::toDepartmentDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                page, // Return 1-based page number as per contract
                limit,
                departments.getTotalElements(),
                departments.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, departmentDtos, pageResponse));
    }

    @PostMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<DepartmentDto> createDepartment(
            @RequestBody DepartmentDto request) {
        
        Department department = new Department();
        department.setDepartmentName(request.getDepartmentName());
        
        if (request.getDepartmentHeadId() != null) {
            User departmentHead = userRepository.findById(request.getDepartmentHeadId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getDepartmentHeadId()));
            department.setDepartmentHead(departmentHead);
        }

        Department saved = departmentRepository.save(department);
        return ResponseEntity.status(201).body(toDepartmentDto(saved));
    }

    @GetMapping("/{departmentId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<DepartmentDto> getDepartment(@PathVariable Integer departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        return ResponseEntity.ok(toDepartmentDto(department));
    }

    @PatchMapping("/{departmentId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<DepartmentDto> updateDepartment(
            @PathVariable Integer departmentId,
            @RequestBody DepartmentDto request) {
        
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        if (request.getDepartmentName() != null) {
            department.setDepartmentName(request.getDepartmentName());
        }
        
        if (request.getDepartmentHeadId() != null) {
            User departmentHead = userRepository.findById(request.getDepartmentHeadId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getDepartmentHeadId()));
            department.setDepartmentHead(departmentHead);
        }

        Department saved = departmentRepository.save(department);
        return ResponseEntity.ok(toDepartmentDto(saved));
    }

    @DeleteMapping("/{departmentId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<?> deleteDepartment(@PathVariable Integer departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        // Check if department has members
        List<User> members = userRepository.findByDepartmentId(departmentId);
        if (!members.isEmpty()) {
            throw new IllegalStateException("Department still has member(s) and cannot be deleted");
        }

        departmentRepository.delete(department);
        return ResponseEntity.noContent().build();
    }

    private DepartmentDto toDepartmentDto(Department department) {
        DepartmentDto dto = new DepartmentDto();
        dto.setId(department.getId());
        dto.setDepartmentName(department.getDepartmentName());

        if (department.getDepartmentHead() != null) {
            dto.setDepartmentHeadId(department.getDepartmentHead().getId().longValue());
            dto.setDepartmentHeadName(department.getDepartmentHead().getName());
        }

        dto.setMemberCount(userRepository.findByDepartmentId(department.getId()).size());

        return dto;
    }
}