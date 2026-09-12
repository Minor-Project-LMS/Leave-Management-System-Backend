package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.Role;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.RoleRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeesController {

    private final UserRepository userRepository;
    private final AttachmentService attachmentService;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeaveLedgerProvisioningService leaveLedgerProvisioningService;

    public EmployeesController(UserRepository userRepository,
                               AttachmentService attachmentService,
                               DepartmentRepository departmentRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder,
                               LeaveLedgerProvisioningService leaveLedgerProvisioningService) {
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.leaveLedgerProvisioningService = leaveLedgerProvisioningService;
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

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("fullName").ascending());
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

        if (userRepository.findByEmailIgnoreCase(employeeInput.getEmail()).isPresent()) {
            throw new BusinessRuleException("Email already in use");
        }

        if (employeeInput.getEmployeeCode() != null &&
                userRepository.findByEmployeeCode(employeeInput.getEmployeeCode()).isPresent()) {
            throw new BusinessRuleException("Employee code already in use");
        }

        User user = new User();
        user.setName(employeeInput.getFullName());
        user.setEmail(employeeInput.getEmail());
        user.setPhone(employeeInput.getPhone());

        // 1. Fetch and set the mandatory Role entity
        String roleCode = employeeInput.getRole() != null ? employeeInput.getRole() : "EMPLOYEE";
        Role role = roleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleCode));
        user.setRole(role);

        // 2. Set a default password hash (satisfies nullable = false)
        user.setPasswordHash(passwordEncoder.encode("Welcome@123"));

        user.setDesignation(employeeInput.getDesignation());
        user.setDateOfJoining(employeeInput.getDateOfJoining());
        user.setEmploymentStatus(employeeInput.getEmploymentStatus() != null ?
                User.EmploymentStatus.valueOf(employeeInput.getEmploymentStatus()) : User.EmploymentStatus.ACTIVE);
        user.setWorkLocation(employeeInput.getWorkLocation());
        user.setEmploymentType(employeeInput.getEmploymentType());

        // 3. Set Manager (Reports To)
        if (employeeInput.getReportsTo() != null) {
            User manager = userRepository.findById(employeeInput.getReportsTo().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("User", employeeInput.getReportsTo()));
            user.setReportsTo(manager);
        }

        // 4. Set Department
        if (employeeInput.getDepartmentId() != null) {
            Department department = departmentRepository.findById((int) employeeInput.getDepartmentId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", employeeInput.getDepartmentId()));
            user.setDepartment(department);
        }

        if (employeeInput.getEmployeeCode() != null) {
            user.setEmployeeCode(employeeInput.getEmployeeCode());
        } else {
            user.setEmployeeCode(generateNextEmployeeCode(roleCode));
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
            Role role = roleRepository.findByRoleCode(employeeInput.getRole())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", employeeInput.getRole()));
            user.setRole(role);
        }
        if (employeeInput.getDepartmentId() != null) {
            Department department = departmentRepository.findById((int) employeeInput.getDepartmentId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", employeeInput.getDepartmentId()));
            user.setDepartment(department);
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

        // Note: we build the response from `user`, not from the return value
        // of save(). save() returns a managed copy from its own short-lived
        // transaction (open-in-view is disabled), and that copy's role /
        // department associations come back as uninitialized lazy proxies
        // with no session left to resolve them — `user` already holds the
        // real, fully-loaded entities (either eagerly fetched above via
        // @EntityGraph, or freshly reassigned in this method), so it's the
        // safe one to serialize.
        userRepository.save(user);
        return ResponseEntity.ok(toUserDto(user));
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

        int fiscalYear = year != null ? year : LocalDate.now().getYear();
        List<LeaveLedger> ledgerEntries = leaveLedgerProvisioningService.getOrInitializeLedger(user, fiscalYear);

        List<LeaveLedgerSummaryDto> ledger = ledgerEntries.stream()
                .map(this::toLeaveLedgerSummaryDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ledger);
    }

    private LeaveLedgerSummaryDto toLeaveLedgerSummaryDto(LeaveLedger ledger) {
        LeaveLedgerSummaryDto dto = new LeaveLedgerSummaryDto();
        dto.setCategoryId(ledger.getCategory().getId());
        dto.setCategoryName(ledger.getCategory().getName());
        dto.setFiscalYear(ledger.getFiscalYear());
        dto.setOpeningBalance(ledger.getOpeningBalance().doubleValue());
        dto.setAccrued(ledger.getAccrued().doubleValue());
        dto.setUsed(ledger.getUsed().doubleValue());
        dto.setEncashed(ledger.getEncashed().doubleValue());
        dto.setCarriedForward(ledger.getCarriedForward().doubleValue());
        dto.setClosingBalance(ledger.getClosingBalance().doubleValue());
        dto.setAvailableBalance(ledger.getClosingBalance().doubleValue());
        return dto;
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

    // Generates the next sequential employee code for a given role, e.g.
    // EMP001, EMP002, EMP003 -> EMP004 for regular employees, HR001 -> HR002
    // for HR admins, MGR001 -> MGR002 for managers. Only codes matching the
    // role's prefix + digits shape are considered, so any legacy/malformed
    // codes (e.g. the old "EMP-<timestamp>" fallback) are ignored rather
    // than blowing up the sequence. Width is preserved from the highest
    // matching code found (defaulting to 3 digits, e.g. EMP001) so the
    // numbering stays consistent as the count grows past 999.
    private String employeeCodePrefixForRole(String roleCode) {
        if (roleCode == null) return "EMP";
        return switch (roleCode) {
            case "HR_ADMIN" -> "HR";
            case "MANAGER" -> "MGR";
            default -> "EMP";
        };
    }

    private String generateNextEmployeeCode(String roleCode) {
        String prefix = employeeCodePrefixForRole(roleCode);
        Pattern codePattern = Pattern.compile("^" + prefix + "(\\d+)$");
        List<User> existing = userRepository.findByEmployeeCodeStartingWith(prefix);

        int maxNumber = 0;
        int width = 3;
        for (User u : existing) {
            String code = u.getEmployeeCode();
            if (code == null) continue;

            Matcher matcher = codePattern.matcher(code);
            if (matcher.matches()) {
                String digits = matcher.group(1);
                int number = Integer.parseInt(digits);
                if (number > maxNumber) {
                    maxNumber = number;
                    width = digits.length();
                }
            }
        }

        int next = maxNumber + 1;
        return String.format(prefix + "%0" + width + "d", next);
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

        // Use the centralized avatar resolver
        dto.setAvatarUrl(attachmentService.resolveAvatarUrl(user.getId()));

        if (user.getReportsTo() != null) {
            dto.setReportsToId(user.getReportsTo().getId());
            // Safe getName() call with null check
            String reportsToName = user.getReportsTo().getName();
            dto.setReportsToName(reportsToName != null ? reportsToName : "Unknown");
        }

        if (user.getDepartment() != null) {
            dto.setDepartmentId(user.getDepartment().getId());
            dto.setDepartmentName(user.getDepartment().getName());
        }

        return dto;
    }
}