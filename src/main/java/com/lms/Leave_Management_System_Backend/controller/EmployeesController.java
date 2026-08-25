package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeesController {

    private final UserRepository userRepository;

    public EmployeesController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<UserDto>> listEmployees(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) User.EmploymentStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("name").ascending());
        Page<User> employees;

        // Apply filters based on parameters
        if (q != null && !q.isBlank()) {
            // Search across name only (simplified) - would need custom query
            employees = userRepository.findAll(pageable);
        } else if (departmentId != null) {
            // Simplified - would need department repository
            employees = userRepository.findAll(pageable);
        } else if (designation != null) {
            // Simplified - would need custom query
            employees = userRepository.findAll(pageable);
        } else if (status != null) {
            // Simplified - would need custom query with Pageable
            employees = userRepository.findAll(pageable);
        } else {
            employees = userRepository.findAll(pageable);
        }

        List<UserDto> dtos = employees.getContent().stream()
                .map(this::toUserDto)
                .toList();

        PageResponse pageResponse = new PageResponse(
                page,
                limit,
                employees.getTotalElements(),
                employees.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtos, pageResponse));
    }

    @PostMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<UserDto> createEmployee(
            @Valid @RequestBody EmployeeInput employeeInput,
            Authentication authentication) {

        // Check if email already exists
        if (userRepository.findByEmailIgnoreCase(employeeInput.getEmail()).isPresent()) {
            throw new BusinessRuleException("Email already in use");
        }

        // Check if employee code already exists
        if (employeeInput.getEmployeeCode() != null && 
            userRepository.findByEmployeeCode(employeeInput.getEmployeeCode()).isPresent()) {
            throw new BusinessRuleException("Employee code already in use");
        }

        User user = new User();
        user.setName(employeeInput.getFullName());
        user.setEmail(employeeInput.getEmail());
        user.setPhone(employeeInput.getPhone());
        // In real implementation, fetch role from repository
        // user.setRole(roleRepository.findByRoleCode(employeeInput.getRole().name()).orElseThrow(...));
        user.setDesignation(employeeInput.getDesignation());
        user.setDateOfJoining(employeeInput.getDateOfJoining());
        user.setEmploymentStatus(employeeInput.getEmploymentStatus() != null ? 
                User.EmploymentStatus.valueOf(employeeInput.getEmploymentStatus()) : User.EmploymentStatus.ACTIVE);
        user.setWorkLocation(employeeInput.getWorkLocation());
        user.setEmploymentType(employeeInput.getEmploymentType());
        
        // Set department and manager if provided
        if (employeeInput.getReportsTo() != null) {
            User manager = userRepository.findById(employeeInput.getReportsTo().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("User", employeeInput.getReportsTo()));
            user.setReportsTo(manager);
        }
        if (employeeInput.getDepartmentId() != null) {
            // In real implementation, fetch department from repository
            // user.setDepartment(departmentRepository.findById(employeeInput.getDepartmentId()).orElseThrow(...));
        }
        
        if (employeeInput.getReportsTo() != null) {
            User manager = userRepository.findById(employeeInput.getReportsTo().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("User", employeeInput.getReportsTo().longValue()));
            user.setReportsTo(manager);
        }

        if (employeeInput.getEmployeeCode() != null) {
            user.setEmployeeCode(employeeInput.getEmployeeCode());
        } else {
            // Generate employee code
            user.setEmployeeCode("EMP-" + System.currentTimeMillis());
        }

        User saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserDto(saved));
    }

    @GetMapping("/{employeeId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<UserDto> getEmployee(
            @PathVariable Long employeeId,
            Authentication authentication) {

        User user = userRepository.findWithReportsToById(employeeId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", employeeId));

        return ResponseEntity.ok(toUserDto(user));
    }

    @PatchMapping("/{employeeId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<UserDto> updateEmployee(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeInput employeeInput,
            Authentication authentication) {

        User user = userRepository.findWithReportsToById(employeeId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", employeeId));

        // Update fields
        if (employeeInput.getFullName() != null) {
            user.setName(employeeInput.getFullName());
        }
        if (employeeInput.getEmail() != null) {
            // Check if email is already used by another user
            userRepository.findByEmailIgnoreCase(employeeInput.getEmail())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(employeeId.longValue())) {
                            throw new BusinessRuleException("Email already in use");
                        }
                    });
            user.setEmail(employeeInput.getEmail());
        }
        if (employeeInput.getPhone() != null) {
            user.setPhone(employeeInput.getPhone());
        }
        if (employeeInput.getRole() != null) {
            // In real implementation, fetch role from repository
            // user.setRole(roleRepository.findByRoleCode(employeeInput.getRole().name()).orElseThrow(...));
        }
        if (employeeInput.getDepartmentId() != null) {
            // In real implementation, fetch department from repository
            // user.setDepartment(departmentRepository.findById(employeeInput.getDepartmentId()).orElseThrow(...));
        }
        if (employeeInput.getDesignation() != null) {
            user.setDesignation(employeeInput.getDesignation());
        }
        if (employeeInput.getReportsTo() != null) {
            User manager = userRepository.findById(employeeInput.getReportsTo().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("User", employeeInput.getReportsTo()));
            user.setReportsTo(manager);
        }
        if (employeeInput.getEmploymentStatus() != null) {
            user.setEmploymentStatus(User.EmploymentStatus.valueOf(employeeInput.getEmploymentStatus()));
        }
        if (employeeInput.getWorkLocation() != null) {
            user.setWorkLocation(employeeInput.getWorkLocation());
        }
        if (employeeInput.getEmploymentType() != null) {
            user.setEmploymentType(employeeInput.getEmploymentType());
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toUserDto(saved));
    }

    @DeleteMapping("/{employeeId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable Long employeeId,
            Authentication authentication) {

        User user = userRepository.findWithReportsToById(employeeId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", employeeId));

        // Set employment status to SEPARATED instead of hard delete
        user.setEmploymentStatus(User.EmploymentStatus.SEPARATED);
        userRepository.save(user);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{employeeId}/leave-ledger")
    @RequireRole({"HR_ADMIN", "MANAGER"})
    public ResponseEntity<List<LeaveLedgerSummaryDto>> getEmployeeLeaveLedger(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            Authentication authentication) {

        User user = userRepository.findWithReportsToById(employeeId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", employeeId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            // Safe access to reportsTo with lazy loading handling
            Long reportsToId = user.getReportsTo() != null ? user.getReportsTo().getId() : null;
            if (reportsToId == null || !reportsToId.equals(currentUser.getId())) {
                throw new BusinessRuleException("You can only view leave ledger for your team members");
            }
        }

        // Simplified implementation - would query actual leave ledger
        LeaveLedgerSummaryDto ledger1 = new LeaveLedgerSummaryDto();
        ledger1.setCategoryId(1);
        ledger1.setCategoryName("Annual Leave");
        ledger1.setFiscalYear(year != null ? year : 2024);
        ledger1.setOpeningBalance(12.0);
        ledger1.setAccrued(0.0);
        ledger1.setUsed(5.0);
        ledger1.setEncashed(0.0);
        ledger1.setCarriedForward(0.0);
        ledger1.setClosingBalance(7.0);
        ledger1.setAvailableBalance(7.0);

        LeaveLedgerSummaryDto ledger2 = new LeaveLedgerSummaryDto();
        ledger2.setCategoryId(2);
        ledger2.setCategoryName("Sick Leave");
        ledger2.setFiscalYear(year != null ? year : 2024);
        ledger2.setOpeningBalance(6.0);
        ledger2.setAccrued(0.0);
        ledger2.setUsed(2.0);
        ledger2.setEncashed(0.0);
        ledger2.setCarriedForward(0.0);
        ledger2.setClosingBalance(4.0);
        ledger2.setAvailableBalance(4.0);

        List<LeaveLedgerSummaryDto> ledger = List.of(ledger1, ledger2);

        return ResponseEntity.ok(ledger);
    }

    @PostMapping("/{employeeId}/quota")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<LeaveLedgerSummaryDto> assignLeaveQuota(
            @PathVariable Long employeeId,
            @Valid @RequestBody LeaveQuotaRequest quotaRequest,
            Authentication authentication) {

        User user = userRepository.findWithReportsToById(employeeId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", employeeId));

        // Simplified implementation - would update actual leave ledger
        LeaveLedgerSummaryDto updated = new LeaveLedgerSummaryDto();
        updated.setCategoryId(quotaRequest.getCategoryId());
        updated.setCategoryName("Leave Category");
        updated.setFiscalYear(quotaRequest.getFiscalYear());
        updated.setOpeningBalance(quotaRequest.getQuota());
        updated.setAccrued(0.0);
        updated.setUsed(0.0);
        updated.setEncashed(0.0);
        updated.setCarriedForward(0.0);
        updated.setClosingBalance(quotaRequest.getQuota());
        updated.setAvailableBalance(quotaRequest.getQuota());

        return ResponseEntity.ok(updated);
    }

    @PostMapping("/import")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Object>> importEmployees(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        // Check file size (10 MB limit as per OpenAPI spec)
        long maxSize = 10 * 1024 * 1024; // 10 MB in bytes
        if (file.getSize() > maxSize) {
            throw new BusinessRuleException("File size exceeds the 10 MB limit");
        }

        // Simplified implementation - would parse CSV/XLSX and import employees
        Map<String, Object> result = new HashMap<>();
        result.put("created", 0);
        result.put("skipped", 0);
        result.put("errors", List.of("Import functionality not fully implemented"));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/export")
    @RequireRole({"HR_ADMIN", "MANAGER"})
    public ResponseEntity<?> exportEmployees(
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(defaultValue = "csv") String format,
            Authentication authentication) {

        // Simplified implementation - would generate actual export file
        // In real implementation, return CSV/XLSX file stream
        return ResponseEntity.ok().build();
    }

    private UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setEmployeeCode(user.getEmployeeCode());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole().getRoleCode());
        dto.setDesignation(user.getDesignation());
        dto.setEmploymentStatus(user.getEmploymentStatus().name());
        dto.setWorkLocation(user.getWorkLocation());
        dto.setEmploymentType(user.getEmploymentType());
        dto.setDateOfJoining(user.getDateOfJoining());
        
        if (user.getReportsTo() != null) {
            dto.setReportsToId(user.getReportsTo().getId());
            // Safe getName() call with null check
            String reportsToName = user.getReportsTo().getName();
            dto.setReportsToName(reportsToName != null ? reportsToName : "Unknown");
        }
        
        // In real implementation, set department info
        // if (user.getDepartment() != null) {
        //     dto.setDepartmentId(user.getDepartment().getId());
        //     dto.setDepartmentName(user.getDepartment().getName());
        // }

        return dto;
    }
}