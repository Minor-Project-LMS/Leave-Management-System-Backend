package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeavePolicy;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.AuditTrailRepository;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeavePolicyRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/leave-policies")
public class LeavePoliciesController {

    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public LeavePoliciesController(
            LeavePolicyRepository leavePolicyRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            DepartmentRepository departmentRepository,
            AuditTrailRepository auditTrailRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.departmentRepository = departmentRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional(readOnly = true)
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
            @RequestBody LeavePolicyRequest request,
            Authentication authentication) {

        LeaveCategory category = leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId()));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
        }

        LeavePolicy policy = new LeavePolicy();
        policy.setPolicyName(request.getPolicyName());
        policy.setPolicyCode(request.getPolicyCode());
        policy.setCategory(category);
        policy.setDepartment(department);
        policy.setAnnualQuota(request.getAnnualQuota() != null ? new java.math.BigDecimal(request.getAnnualQuota()) : java.math.BigDecimal.ZERO);
        policy.setMaxCarryForward(request.getMaxCarryForward() != null ? new java.math.BigDecimal(request.getMaxCarryForward()) : java.math.BigDecimal.ZERO);
        policy.setMinNoticeDays(request.getMinNoticeDays() != null ? request.getMinNoticeDays() : 0);
        policy.setMaxConsecutiveDays(request.getMaxConsecutiveDays() != null ? request.getMaxConsecutiveDays() : 0);
        if (request.getAccrualFrequency() != null) {
            policy.setAccrualFrequency(request.getAccrualFrequency());
        }
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setStatus(request.getStatus() != null ? request.getStatus() : "DRAFT");

        leavePolicyRepository.save(policy);

        recordPolicyHistory(policy, AuditTrail.AuditAction.CREATE, null, snapshotPolicy(policy), authentication);

        LeavePolicyDto dto = toLeavePolicyDto(policy);

        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping("/{policyId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional(readOnly = true)
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
            @RequestBody LeavePolicyRequest request,
            Authentication authentication) {

        LeavePolicy policy = leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("LeavePolicy", policyId));

        Map<String, Object> before = snapshotPolicy(policy);

        if (request.getPolicyName() != null) {
            policy.setPolicyName(request.getPolicyName());
        }
        if (request.getPolicyCode() != null) {
            policy.setPolicyCode(request.getPolicyCode());
        }
        if (request.getAccrualFrequency() != null) {
            policy.setAccrualFrequency(request.getAccrualFrequency());
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

        // Build the response from `policy` (already holds the real,
        // fully-loaded category/department we just set), not from the
        // return value of save(). Same issue as the earlier Employees
        // fix: save() returns a merged copy from its own short-lived
        // transaction, and that copy's associations can come back as
        // uninitialized lazy proxies with no session left to resolve
        // them once open-in-view is disabled.
        leavePolicyRepository.save(policy);

        Map<String, Object> after = snapshotPolicy(policy);
        recordPolicyHistory(policy, AuditTrail.AuditAction.UPDATE, before, after, authentication);

        return ResponseEntity.ok(toLeavePolicyDto(policy));
    }

    @GetMapping("/{policyId}/history")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<List<LeavePolicyHistoryEntryDto>> getPolicyHistory(
            @PathVariable Integer policyId) {

        // Make sure the policy actually exists (404 if not) — the real
        // history comes from audit entries recorded at create/update
        // time, not from guessing based on other policies that happen to
        // share the same category/department. That's what this endpoint
        // used to do, which incorrectly surfaced unrelated policies as
        // "versions" of each other.
        leavePolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("LeavePolicy", policyId));

        List<AuditTrail> entries = auditTrailRepository
                .findByEntityTypeAndEntityIdOrderByPerformedAtDesc("LEAVE_POLICY", policyId.longValue());

        List<LeavePolicyHistoryEntryDto> dtos = entries.stream()
                .map(this::toHistoryEntryDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private LeavePolicyHistoryEntryDto toHistoryEntryDto(AuditTrail entry) {
        LeavePolicyHistoryEntryDto dto = new LeavePolicyHistoryEntryDto();
        dto.setId(entry.getId());
        dto.setAction(entry.getAction() == AuditTrail.AuditAction.CREATE ? "CREATED" : "UPDATED");
        dto.setPerformedByName(entry.getPerformedBy() != null ? entry.getPerformedBy().getFullName() : "System");
        dto.setPerformedAt(entry.getPerformedAt());

        if (entry.getAction() == AuditTrail.AuditAction.CREATE) {
            dto.setSummary("Policy created.");
            dto.setChangedFields(java.util.Collections.emptyList());
            return dto;
        }

        try {
            Map<String, Object> before = entry.getBeforeState() != null
                    ? objectMapper.readValue(entry.getBeforeState(), Map.class) : Map.of();
            Map<String, Object> after = entry.getAfterState() != null
                    ? objectMapper.readValue(entry.getAfterState(), Map.class) : Map.of();
            List<String> changed = diffFieldNames(before, after);
            dto.setChangedFields(changed);
            dto.setSummary(changed.isEmpty() ? "Updated." : "Updated " + String.join(", ", changed) + ".");
        } catch (Exception ex) {
            dto.setSummary("Policy updated.");
            dto.setChangedFields(java.util.Collections.emptyList());
        }

        return dto;
    }

    // Field-label snapshot of everything that shows up in the Edit Policy
    // form, taken before and after a change so the two can be diffed into
    // a human-readable "Updated X, Y" summary — rather than every edit
    // just saying "UPDATED" with no indication of what actually changed.
    private Map<String, Object> snapshotPolicy(LeavePolicy p) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("Policy Name", p.getPolicyName());
        snapshot.put("Policy Code", p.getPolicyCode());
        snapshot.put("Leave Type", p.getCategory() != null ? p.getCategory().getName() : null);
        snapshot.put("Department", p.getDepartment() != null ? p.getDepartment().getDepartmentName() : "Organization-wide");
        snapshot.put("Annual Quota", p.getAnnualQuota());
        snapshot.put("Accrual Frequency", p.getAccrualFrequency());
        snapshot.put("Max Carry Forward", p.getMaxCarryForward());
        snapshot.put("Max Consecutive Days", p.getMaxConsecutiveDays());
        snapshot.put("Min Notice Days", p.getMinNoticeDays());
        snapshot.put("Effective From", p.getEffectiveFrom());
        snapshot.put("Status", p.getStatus());
        return snapshot;
    }

    private List<String> diffFieldNames(Map<String, Object> before, Map<String, Object> after) {
        List<String> changed = new java.util.ArrayList<>();
        if (before == null || after == null) return changed;
        for (String key : after.keySet()) {
            Object oldVal = normalizeForCompare(before.get(key));
            Object newVal = normalizeForCompare(after.get(key));
            if (!java.util.Objects.equals(oldVal, newVal)) {
                changed.add(key);
            }
        }
        return changed;
    }

    private Object normalizeForCompare(Object value) {
        if (value instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        return value == null ? null : value.toString();
    }

    private void recordPolicyHistory(LeavePolicy policy, AuditTrail.AuditAction action,
                                     Map<String, Object> before, Map<String, Object> after,
                                     Authentication authentication) {
        try {
            if (action == AuditTrail.AuditAction.UPDATE && diffFieldNames(before, after).isEmpty()) {
                // Nothing actually changed (e.g. Save was clicked with no
                // edits) — don't clutter history with a no-op entry.
                return;
            }

            User currentUser = null;
            if (authentication != null) {
                currentUser = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
            }

            AuditTrail entry = new AuditTrail();
            entry.setEntityType("LEAVE_POLICY");
            entry.setEntityId(policy.getId().longValue());
            entry.setAction(action);
            entry.setPerformedBy(currentUser);
            entry.setBeforeState(before != null ? objectMapper.writeValueAsString(before) : null);
            entry.setAfterState(after != null ? objectMapper.writeValueAsString(after) : null);
            auditTrailRepository.save(entry);
        } catch (Exception ex) {
            // History logging should never break the actual create/update.
        }
    }

    private LeavePolicyDto toLeavePolicyDto(LeavePolicy policy) {
        LeavePolicyDto dto = new LeavePolicyDto();
        dto.setPolicyId(policy.getId());
        dto.setPolicyName(policy.getPolicyName());
        dto.setPolicyCode(policy.getPolicyCode());

        LeaveCategoryDto categoryDto = new LeaveCategoryDto();
        categoryDto.setId(policy.getCategory().getId());
        categoryDto.setName(policy.getCategory().getName());
        categoryDto.setCategoryCode(policy.getCategory().getCategoryCode());
        categoryDto.setCategoryType(policy.getCategory().getCategoryType());
        categoryDto.setApplicableTo(policy.getCategory().getApplicableTo());
        categoryDto.setPaid(policy.getCategory().isPaid());
        categoryDto.setRequiresDocument(policy.getCategory().isRequiresDocument());
        categoryDto.setDefaultAnnualQuota(policy.getCategory().getDefaultAnnualQuota());
        categoryDto.setStatus(policy.getCategory().getStatus());
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
        dto.setAccrualFrequency(policy.getAccrualFrequency());
        dto.setStatus(policy.getStatus());

        return dto;
    }
}