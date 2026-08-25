package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeavePolicy;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeavePolicyRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/leave-policies")
public class LeavePoliciesController {

    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final DepartmentRepository departmentRepository;

    public LeavePoliciesController(
            LeavePolicyRepository leavePolicyRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            DepartmentRepository departmentRepository) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeavePolicyDto>> listLeavePolicies(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort) {

        Pageable pageable = PageRequest.of(page - 1, limit,
            sort != null ? Sort.by(sort) : Sort.by("effectiveFrom").descending());
        
        Page<LeavePolicy> policies;
        if (categoryId != null || departmentId != null) {
            policies = leavePolicyRepository.findWithFilters(categoryId, departmentId, pageable);
        } else {
            policies = leavePolicyRepository.findAll(pageable);
        }

        List<LeavePolicyDto> policyDtos = policies.getContent().stream()
                .map(this::toLeavePolicyDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
            page,
            limit,
            policies.getTotalElements(),
            policies.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, policyDtos, pageResponse));
    }

    @PostMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<LeavePolicyDto> createLeavePolicy(
            @RequestBody LeavePolicyRequest request) {
        
        LeaveCategory category = leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId()));
        
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
        }

        LeavePolicy policy = new LeavePolicy();
        policy.setCategory(category);
        policy.setDepartment(department);
        policy.setAnnualQuota(request.getAnnualQuota() != null ? new java.math.BigDecimal(request.getAnnualQuota()) : java.math.BigDecimal.ZERO);
        policy.setMaxCarryForward(request.getMaxCarryForward() != null ? new java.math.BigDecimal(request.getMaxCarryForward()) : java.math.BigDecimal.ZERO);
        policy.setMinNoticeDays(request.getMinNoticeDays() != null ? request.getMinNoticeDays() : 0);
        policy.setMaxConsecutiveDays(request.getMaxConsecutiveDays() != null ? request.getMaxConsecutiveDays() : 0);
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setStatus("DRAFT"); // New policies start as DRAFT

        LeavePolicy saved = leavePolicyRepository.save(policy);
        LeavePolicyDto dto = toLeavePolicyDto(saved);
        
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping("/{policyId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<LeavePolicyDto> getLeavePolicy(
            @PathVariable Integer policyId) {
        
        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("LeavePolicy", policyId));
        
        LeavePolicyDto dto = toLeavePolicyDto(policy);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{policyId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<LeavePolicyDto> updateLeavePolicy(
            @PathVariable Integer policyId,
            @RequestBody LeavePolicyRequest request) {
        
        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("LeavePolicy", policyId));

        // Only DRAFT policies can be fully edited
        if (!"DRAFT".equals(policy.getStatus())) {
            throw new IllegalStateException("Only DRAFT policies can be fully edited");
        }

        if (request.getCategoryId() != null) {
            LeaveCategory category = leaveCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId()));
            policy.setCategory(category);
        }
        
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            policy.setDepartment(department);
        }

        if (request.getAnnualQuota() != null) {
            policy.setAnnualQuota(new java.math.BigDecimal(request.getAnnualQuota()));
        }
        if (request.getMaxCarryForward() != null) {
            policy.setMaxCarryForward(new java.math.BigDecimal(request.getMaxCarryForward()));
        }
        if (request.getMinNoticeDays() != null) {
            policy.setMinNoticeDays(request.getMinNoticeDays());
        }
        if (request.getMaxConsecutiveDays() != null) {
            policy.setMaxConsecutiveDays(request.getMaxConsecutiveDays());
        }
        if (request.getEffectiveFrom() != null) {
            policy.setEffectiveFrom(request.getEffectiveFrom());
        }
        if (request.getStatus() != null) {
            policy.setStatus(request.getStatus());
        }

        LeavePolicy saved = leavePolicyRepository.save(policy);
        return ResponseEntity.ok(toLeavePolicyDto(saved));
    }

    @GetMapping("/{policyId}/history")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<List<LeavePolicyDto>> getPolicyHistory(
            @PathVariable Integer policyId) {
        
        LeavePolicy currentPolicy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("LeavePolicy", policyId));

        // Get all policies for the same category and department
        List<LeavePolicy> history;
        if (currentPolicy.getDepartment() != null) {
            history = leavePolicyRepository.findByCategoryId(currentPolicy.getCategory().getId()).stream()
                    .filter(p -> p.getDepartment() != null && p.getDepartment().getId().equals(currentPolicy.getDepartment().getId()))
                    .collect(Collectors.toList());
        } else {
            history = leavePolicyRepository.findByCategoryIdAndDepartmentIdIsNull(
                    currentPolicy.getCategory().getId());
        }

        return ResponseEntity.ok(history.stream()
                .map(this::toLeavePolicyDto)
                .collect(Collectors.toList()));
    }

    private LeavePolicyDto toLeavePolicyDto(LeavePolicy policy) {
        LeavePolicyDto dto = new LeavePolicyDto();
        dto.setPolicyId(policy.getId());

        LeaveCategoryDto categoryDto = new LeaveCategoryDto();
        categoryDto.setId(policy.getCategory().getId());
        categoryDto.setName(policy.getCategory().getName());
        categoryDto.setPaid(policy.getCategory().isPaid());
        categoryDto.setRequiresDocument(policy.getCategory().isRequiresDocument());
        categoryDto.setDefaultAnnualQuota(policy.getCategory().getDefaultAnnualQuota());
        categoryDto.setActive(true); // Default to true
        dto.setCategory(categoryDto);

        if (policy.getDepartment() != null) {
            DepartmentDto departmentDto = new DepartmentDto(
                policy.getDepartment().getId(),
                policy.getDepartment().getDepartmentName()
            );
            if (policy.getDepartment().getDepartmentHead() != null) {
                departmentDto.setDepartmentHeadId(policy.getDepartment().getDepartmentHead().getId().longValue());
                // Safe getName() call with null check
                String headName = policy.getDepartment().getDepartmentHead().getName();
                departmentDto.setDepartmentHeadName(headName != null ? headName : "Unknown");
            }
            dto.setDepartment(departmentDto);
        }

        dto.setAnnualQuota(policy.getAnnualQuota() != null ? policy.getAnnualQuota().doubleValue() : 0.0);
        dto.setMaxCarryForward(policy.getMaxCarryForward() != null ? policy.getMaxCarryForward().doubleValue() : 0.0);
        dto.setMinNoticeDays(policy.getMinNoticeDays() != null ? policy.getMinNoticeDays() : 0);
        dto.setMaxConsecutiveDays(policy.getMaxConsecutiveDays() != null ? policy.getMaxConsecutiveDays() : 0);
        dto.setEffectiveFrom(policy.getEffectiveFrom());
        dto.setStatus("ACTIVE"); // Default to ACTIVE

        return dto;
    }
}