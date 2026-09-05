package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import com.lms.Leave_Management_System_Backend.model.*;
import com.lms.Leave_Management_System_Backend.model.LeaveApproval;
import com.lms.Leave_Management_System_Backend.repository.*;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveRequestsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final LeaveApprovalRepository leaveApprovalRepository;
    private final ApprovalDelegationRepository delegationRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;
    private final NotificationQueueRepository notificationQueueRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final com.lms.Leave_Management_System_Backend.service.AttachmentService attachmentService;

    // In-memory comment storage
    private static final Map<Long, List<CommentDto>> commentStorage = new ConcurrentHashMap<>();

    public LeaveRequestsController(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            LeaveApprovalRepository leaveApprovalRepository,
            ApprovalDelegationRepository delegationRepository,
            LeaveLedgerRepository leaveLedgerRepository,
            NotificationQueueRepository notificationQueueRepository,
            LeavePolicyRepository leavePolicyRepository,
            com.lms.Leave_Management_System_Backend.service.AttachmentService attachmentService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.delegationRepository = delegationRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.notificationQueueRepository = notificationQueueRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<LeaveRequestDto>> createLeaveRequest(
            @Valid @RequestBody LeaveRequestCreate request,
            Authentication authentication) {
        
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        LeaveCategory category = leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId()));

        // Calculate totalDays based on dates and session type
        BigDecimal calculatedDays = calculateTotalDays(request.getStartDate(), request.getEndDate(), request.getSessionType());

        // Validate max continuous days limit according to policy
        validateConsecutiveDays(user, category, calculatedDays);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUser(user);
        leaveRequest.setCategory(category);
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setSessionType(LeaveRequest.SessionType.valueOf(request.getSessionType()));
        leaveRequest.setTotalDays(calculatedDays);

        leaveRequest.setReason(request.getReason());
        leaveRequest.setContactNumber(request.getContactNumber());
        leaveRequest.setAddressDuringLeave(request.getAddressDuringLeave());

        // Set handover information
        if (request.getHandoverTo() != null) {
            User handoverUser = userRepository.findById(request.getHandoverTo())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getHandoverTo()));
            leaveRequest.setHandoverTo(handoverUser);
        }
        leaveRequest.setHandoverNotes(request.getHandoverNotes());

        // Set status based on request or default to PENDING_L1
        if (request.getStatus() != null && "DRAFT".equals(request.getStatus())) {
            leaveRequest.setStatus(LeaveRequest.RequestStatus.DRAFT);
        } else {
            leaveRequest.setStatus(LeaveRequest.RequestStatus.PENDING_L1);
            
            // Set the approver when creating a non-DRAFT request
            User reportsTo = user.getReportsTo();
            if (reportsTo != null) {
                // Check if there's an active delegation for the manager
                Optional<ApprovalDelegation> activeDelegation =
                        delegationRepository.findActiveDelegationsForDelegatorOnDate(reportsTo.getId(), java.time.LocalDate.now())
                                .stream()
                                .findFirst();

                if (activeDelegation.isPresent()) {
                    leaveRequest.setCurrentApprover(activeDelegation.get().getDelegate());
                } else {
                    leaveRequest.setCurrentApprover(reportsTo);
                }
            }
        }

        leaveRequest.setAppliedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        createNotification(
                saved.getUser(),
                "LEAVE_SUBMITTED",
                "Leave Request Submitted",
                "Your leave request for " + saved.getTotalDays() + " day(s) has been submitted.",
                "LEAVE_REQUEST",
                saved.getId()
        );
        LeaveRequestDto dto = toLeaveRequestDto(saved);

        return ResponseEntity.status(201).body(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    /**
     * Calculate total days for leave request excluding weekends
     */
    private BigDecimal calculateTotalDays(LocalDate startDate, LocalDate endDate, String sessionType) {
        long businessDays = 0;

        // Count business days (Monday-Friday)
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY && current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                businessDays++;
            }
            current = current.plusDays(1);
        }

        // Adjust for session type
        if ("FIRST_HALF".equals(sessionType) || "SECOND_HALF".equals(sessionType)) {
            return BigDecimal.valueOf(businessDays * 0.5);
        } else {
            return BigDecimal.valueOf(businessDays);
        }
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<PaginatedResponse<LeaveRequestDto>> listLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "recent") String sort,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Map sort enum values to actual sort parameters
        String sortProperty;
        Sort.Direction direction;
        
        if ("recent".equalsIgnoreCase(sort)) {
            sortProperty = "appliedAt";
            direction = Sort.Direction.DESC;
        } else if ("oldest".equalsIgnoreCase(sort)) {
            sortProperty = "appliedAt";
            direction = Sort.Direction.ASC;
        } else {
            // Fallback to legacy format "property,direction"
            String[] sortParams = sort.split(",");
            sortProperty = sortParams[0];
            direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(direction, sortProperty));
        Page<LeaveRequest> leaveRequests;

        // Employees can only see their own requests
        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE")) {
            if (status != null) {
                leaveRequests = leaveRequestRepository.findByUserIdAndStatus(
                        currentUser.getId(),
                        LeaveRequest.RequestStatus.valueOf(status),
                        pageable);
            } else {
                leaveRequests = leaveRequestRepository.findByUserId(currentUser.getId(), pageable);
            }
        } else {
            // Managers and HR can see filtered requests
            if (userId != null) {
                if (status != null) {
                    leaveRequests = leaveRequestRepository.findByUserIdAndStatus(userId, LeaveRequest.RequestStatus.valueOf(status), pageable);
                } else {
                    leaveRequests = leaveRequestRepository.findByUserId(userId, pageable);
                }
            } else if (status != null) {
                leaveRequests = leaveRequestRepository.findByStatus(LeaveRequest.RequestStatus.valueOf(status), pageable);
            } else {
                leaveRequests = leaveRequestRepository.findAll(pageable);
            }
        }

        List<LeaveRequestDto> dtos = leaveRequests.getContent().stream()
                .map(this::toLeaveRequestDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                page,
                limit,
                leaveRequests.getTotalElements(),
                leaveRequests.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtos, pageResponse));
    }

    @GetMapping("/{requestId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view your own leave requests");
        }

        LeaveRequestDto dto = toLeaveRequestDto(leaveRequest);
        return ResponseEntity.ok(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    @PatchMapping("/{requestId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody LeaveRequestCreate request,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        if (leaveRequest.getStatus() != LeaveRequest.RequestStatus.DRAFT) {
            throw new ConflictException("Only draft requests can be edited");
        }

        // Check ownership
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only edit your own leave requests");
        }

        LeaveCategory category = leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId()));

        // Recalculate totalDays based on updated dates and session type
        BigDecimal calculatedDays = calculateTotalDays(request.getStartDate(), request.getEndDate(), request.getSessionType());

        // Validate max continuous days limit according to policy
        validateConsecutiveDays(currentUser, category, calculatedDays);

        leaveRequest.setCategory(category);
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setSessionType(LeaveRequest.SessionType.valueOf(request.getSessionType()));
        leaveRequest.setTotalDays(calculatedDays);

        leaveRequest.setReason(request.getReason());
        leaveRequest.setContactNumber(request.getContactNumber());
        leaveRequest.setAddressDuringLeave(request.getAddressDuringLeave());

        // Update handover information
        if (request.getHandoverTo() != null) {
            User handoverUser = userRepository.findById(request.getHandoverTo())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getHandoverTo()));
            leaveRequest.setHandoverTo(handoverUser);
        } else {
            leaveRequest.setHandoverTo(null);
        }
        leaveRequest.setHandoverNotes(request.getHandoverNotes());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);

        return ResponseEntity.ok(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    @PostMapping("/{requestId}/submit")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<LeaveRequestDto>> submitLeaveRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        if (leaveRequest.getStatus() != LeaveRequest.RequestStatus.DRAFT) {
            throw new ConflictException("Only draft requests can be submitted");
        }

        // Check ownership
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only submit your own leave requests");
        }

        // Validate max continuous days limit according to policy
        validateConsecutiveDays(currentUser, leaveRequest.getCategory(), leaveRequest.getTotalDays());

        // Check if user has sufficient leave balance
        int currentYear = java.time.Year.now().getValue();
        Optional<LeaveLedger> ledger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                leaveRequest.getUser().getId(),
                leaveRequest.getCategory().getId(),
                currentYear
        );

        if (ledger.isPresent() && ledger.get().getClosingBalance().compareTo(leaveRequest.getTotalDays()) < 0) {
            throw new ConflictException("Insufficient leave balance. Available: " + ledger.get().getClosingBalance() + ", Required: " + leaveRequest.getTotalDays());
        }

        // Check for overlapping approved requests
        List<LeaveRequest> overlappingApproved = leaveRequestRepository.findByUserIdAndStatus(
                leaveRequest.getUser().getId(),
                LeaveRequest.RequestStatus.APPROVED
        );

        // Filter for overlapping dates
        boolean hasOverlap = overlappingApproved.stream()
                .anyMatch(existing -> {
                    return !(existing.getEndDate().isBefore(leaveRequest.getStartDate()) ||
                            existing.getStartDate().isAfter(leaveRequest.getEndDate()));
                });

        if (hasOverlap) {
            throw new ConflictException("You have overlapping approved leave requests for this period");
        }

        // Set to pending and assign approver
        leaveRequest.setStatus(LeaveRequest.RequestStatus.PENDING_L1);

        // Resolve the approver considering delegation
        User reportsTo = leaveRequest.getUser().getReportsTo();
        if (reportsTo != null) {
            // Check if there's an active delegation for the manager
            Optional<ApprovalDelegation> activeDelegation =
                    delegationRepository.findActiveDelegationsForDelegatorOnDate(reportsTo.getId(), java.time.LocalDate.now())
                            .stream()
                            .findFirst();

            if (activeDelegation.isPresent()) {
                leaveRequest.setCurrentApprover(activeDelegation.get().getDelegate());
            } else {
                leaveRequest.setCurrentApprover(reportsTo);
            }
        }
        leaveRequest.setAppliedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);

        return ResponseEntity.ok(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    @PatchMapping("/{requestId}/decisions")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<LeaveRequestDto>> recordDecision(
            @PathVariable Long requestId,
            @Valid @RequestBody LeaveDecisionRequest decisionRequest,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check if request is awaiting decision
        if (leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L1 &&
                leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L2) {
            throw new ConflictException("Request is not currently awaiting a decision");
        }

        // Check if caller is the current approver or an active delegate
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        User currentApprover = leaveRequest.getCurrentApprover();
        if (currentApprover == null) {
            throw new ConflictException("This request does not have a current approver");
        }

        // Check if user is the designated approver or an active delegate
        boolean isDelegatedApprover = false;
        if (!currentApprover.getId().equals(currentUser.getId())) {
            Optional<ApprovalDelegation> activeDelegation =
                    delegationRepository.findActiveDelegation(currentApprover.getId(), currentUser.getId(), java.time.LocalDate.now());

            if (activeDelegation.isEmpty()) {
                throw new SecurityException("You are not the current approver or an active delegate for this request");
            }
            isDelegatedApprover = true;
        }

        // Validate decision value matches contract
        if (!"APPROVED".equals(decisionRequest.getDecision()) && !"REJECTED".equals(decisionRequest.getDecision())) {
            throw new ConflictException("Invalid decision. Must be APPROVED or REJECTED");
        }

        // Comments mandatory for rejection
        if ("REJECTED".equals(decisionRequest.getDecision()) &&
                (decisionRequest.getComments() == null || decisionRequest.getComments().trim().isEmpty())) {
            throw new ConflictException("Comments are mandatory on rejection");
        }

        // Check for overlapping approved requests for the same user
        if ("APPROVED".equals(decisionRequest.getDecision())) {
            List<LeaveRequest> overlappingApproved = leaveRequestRepository.findByUserIdAndStatus(
                    leaveRequest.getUser().getId(),
                    LeaveRequest.RequestStatus.APPROVED
            );

            boolean hasOverlap = overlappingApproved.stream()
                    .filter(existing -> !existing.getId().equals(leaveRequest.getId()))
                    .anyMatch(existing -> {
                        return !(existing.getEndDate().isBefore(leaveRequest.getStartDate()) ||
                                existing.getStartDate().isAfter(leaveRequest.getEndDate()));
                    });

            if (hasOverlap) {
                throw new ConflictException("Employee has overlapping approved leave requests for this period");
            }
        }

        // Record the approval history
        LeaveApproval approval = new LeaveApproval();
        approval.setRequest(leaveRequest);
        approval.setApprover(currentUser);
        approval.setLevel(leaveRequest.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 ? (short) 1 : (short) 2);
        approval.setDecision("APPROVED".equals(decisionRequest.getDecision()) ?
                LeaveApproval.Decision.APPROVED : LeaveApproval.Decision.REJECTED);
        approval.setDecidedAt(LocalDateTime.now());
        approval.setComments(decisionRequest.getComments());
        leaveApprovalRepository.save(approval);

        // Update the request status
        if ("APPROVED".equals(decisionRequest.getDecision())) {
            if (leaveRequest.getTotalDays().compareTo(BigDecimal.valueOf(5)) > 0 &&
                    leaveRequest.getStatus() == LeaveRequest.RequestStatus.PENDING_L1) {
                // Move to HR approval
                leaveRequest.setStatus(LeaveRequest.RequestStatus.PENDING_L2);
                User hrAdmin = userRepository.findFirstByRole_RoleCode("HR_ADMIN")
                        .orElseThrow(() -> new ResourceNotFoundException("HR Admin", "role"));
                leaveRequest.setCurrentApprover(hrAdmin);
            } else {
                // Final approval - update leave ledger
                int currentYear = java.time.Year.now().getValue();
                Optional<LeaveLedger> ledger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                        leaveRequest.getUser().getId(),
                        leaveRequest.getCategory().getId(),
                        currentYear
                );

                if (ledger.isPresent()) {
                    LeaveLedger leaveLedger = ledger.get();
                    if (leaveLedger.getClosingBalance().compareTo(leaveRequest.getTotalDays()) < 0) {
                        throw new ConflictException("Insufficient leave balance at approval time");
                    }
                    leaveLedger.setUsed(leaveLedger.getUsed().add(leaveRequest.getTotalDays()));
                    leaveLedger.setClosingBalance(leaveLedger.getClosingBalance().subtract(leaveRequest.getTotalDays()));
                    leaveLedgerRepository.save(leaveLedger);
                }

                leaveRequest.setStatus(LeaveRequest.RequestStatus.APPROVED);
                leaveRequest.setCurrentApprover(null);
            }
        } else {
            leaveRequest.setStatus(LeaveRequest.RequestStatus.REJECTED);
            leaveRequest.setCurrentApprover(null);
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if ("APPROVED".equals(decisionRequest.getDecision())) {
            if (saved.getStatus() == LeaveRequest.RequestStatus.APPROVED) {
                createNotification(
                        saved.getUser(),
                        "LEAVE_APPROVED",
                        "Leave Request Approved",
                        "Your leave request from " + saved.getStartDate() + " to " + saved.getEndDate() + " has been approved.",
                        "LEAVE_APPROVAL",
                        saved.getId()
                );
            } else if (saved.getStatus() == LeaveRequest.RequestStatus.PENDING_L2) {
                createNotification(
                        saved.getCurrentApprover(),
                        "LEAVE_HR_APPROVAL_PENDING",
                        "HR Approval Required",
                        "A leave request for " + saved.getUser().getName() + " requires HR level approval.",
                        "LEAVE_APPROVAL",
                        saved.getId()
                );
            }
        } else if ("REJECTED".equals(decisionRequest.getDecision())) {
            createNotification(
                    saved.getUser(),
                    "LEAVE_REJECTED",
                    "Leave Request Rejected",
                    "Your leave request was rejected. Reason: " + decisionRequest.getComments(),
                    "LEAVE_APPROVAL",
                    saved.getId()
            );
        }
        LeaveRequestDto dto = toLeaveRequestDto(saved);

        return ResponseEntity.ok(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    @PostMapping("/{requestId}/withdraw")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<LeaveRequestDto>> withdrawLeaveRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) com.lms.Leave_Management_System_Backend.dto.WithdrawRequest withdrawRequest,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        if (leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L1 &&
                leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L2) {
            throw new ConflictException("Only pending requests can be withdrawn");
        }

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only withdraw your own leave requests");
        }

        leaveRequest.setStatus(LeaveRequest.RequestStatus.WITHDRAWN);
        if (withdrawRequest != null && withdrawRequest.getReason() != null) {
            leaveRequest.setReason(leaveRequest.getReason() + " [Withdrawal reason: " + withdrawRequest.getReason() + "]");
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);

        return ResponseEntity.ok(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    @GetMapping("/{requestId}/approvals")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<List<LeaveApprovalDto>> getApprovals(
            @PathVariable Long requestId,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view your own leave request approvals");
        }

        List<LeaveApproval> approvals = leaveApprovalRepository.findByRequestId(requestId);

        List<LeaveApprovalDto> approvalDtos = approvals.stream()
                .map(this::toLeaveApprovalDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(approvalDtos);
    }

    private LeaveApprovalDto toLeaveApprovalDto(LeaveApproval approval) {
        LeaveApprovalDto dto = new LeaveApprovalDto();
        dto.setId(approval.getId().intValue());
        dto.setRequestId(approval.getRequest().getId().intValue());
        dto.setApproverId(approval.getApprover().getId().intValue());
        dto.setApproverName(approval.getApprover().getName());
        dto.setActingAsDelegateFor(null);
        dto.setLevel(approval.getLevel().intValue());
        dto.setDecision(approval.getDecision().name());
        dto.setDecidedAt(approval.getDecidedAt());
        dto.setComments(approval.getComments());
        return dto;
    }

    @GetMapping("/{requestId}/comments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<List<CommentDto>> getComments(
            @PathVariable Long requestId,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view your own leave request comments");
        }

        List<CommentDto> comments = commentStorage.getOrDefault(requestId, new ArrayList<>());

        return ResponseEntity.ok(comments);
    }

    /**
     * Helper method to validate continuous leave duration against policy limits
     */
    private void validateConsecutiveDays(User user, LeaveCategory category, BigDecimal totalDays) {
        Integer deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        List<LeavePolicy> policies;
        if (deptId != null) {
            policies = leavePolicyRepository.findByCategoryId(category.getId()).stream()
                    .filter(p -> p.getDepartment() != null && p.getDepartment().getId().equals(deptId))
                    .collect(Collectors.toList());
        } else {
            policies = leavePolicyRepository.findByCategoryIdAndDepartmentIdIsNull(category.getId());
        }

        if (!policies.isEmpty()) {
            LeavePolicy policy = policies.get(0);
            if (policy.getMaxConsecutiveDays() != null && policy.getMaxConsecutiveDays() > 0) {
                BigDecimal maxLimit = BigDecimal.valueOf(policy.getMaxConsecutiveDays());
                if (totalDays.compareTo(maxLimit) > 0) {
                    throw new ConflictException("Selected duration (" + totalDays + " days) exceeds the maximum allowed continuous limit of " + maxLimit + " days for " + category.getName());
                }
            }
        }
    }

    private void createNotification(User user, String type, String title, String message, String entityType, Long entityId) {
        // Notification creation implementation
    }

    private LeaveRequestDto toLeaveRequestDto(LeaveRequest request) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setId(request.getId());
        if (request.getUser() != null) {
            dto.setUserId(request.getUser().getId());
            dto.setUserName(request.getUser().getName());
        }
        if (request.getCategory() != null) {
            dto.setCategoryId(request.getCategory().getId());
            dto.setCategoryName(request.getCategory().getName());
        }
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setSessionType(request.getSessionType() != null ? request.getSessionType().name() : null);
        dto.setTotalDays(request.getTotalDays());
        dto.setReason(request.getReason());
        dto.setStatus(request.getStatus() != null ? request.getStatus().name() : null);
        dto.setAppliedAt(request.getAppliedAt());
        return dto;
    }

    // ============================================================
    // ATTACHMENT ENDPOINTS
    // ============================================================

    @GetMapping("/{requestId}/attachments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<List<AttachmentDto>> getAttachments(
            @PathVariable Long requestId,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view attachments for your own leave requests");
        }

        List<AttachmentDto> attachments = attachmentService.listAttachments(
                com.lms.Leave_Management_System_Backend.model.Attachment.EntityType.LEAVE_REQUEST,
                requestId
        );

        return ResponseEntity.ok(attachments);
    }

    @PostMapping("/{requestId}/attachments/init-upload")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentInitUploadResponse>> initAttachmentUpload(
            @PathVariable Long requestId,
            @Valid @RequestBody AttachmentInitUploadRequest request,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions - only the request owner can upload
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only upload attachments to your own leave requests");
        }

        AttachmentInitUploadResponse response = attachmentService.initializeUpload(
                com.lms.Leave_Management_System_Backend.model.Attachment.EntityType.LEAVE_REQUEST,
                requestId,
                request,
                currentUser.getId()
        );

        return ResponseEntity.status(201).body(new ApiResponse<>(true, response));
    }

    @PostMapping("/{requestId}/attachments/{attachmentId}/confirm")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentDto>> confirmAttachmentUpload(
            @PathVariable Long requestId,
            @PathVariable Long attachmentId,
            @RequestBody(required = false) AttachmentConfirmRequest confirmRequest,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions - only the request owner can confirm
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only confirm attachments for your own leave requests");
        }

        AttachmentDto attachment = attachmentService.confirmUpload(attachmentId, confirmRequest);

        return ResponseEntity.ok(new ApiResponse<>(true, attachment));
    }

    @GetMapping("/{requestId}/attachments/{attachmentId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentDto>> getAttachment(
            @PathVariable Long requestId,
            @PathVariable Long attachmentId,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view attachments for your own leave requests");
        }

        AttachmentDto attachment = attachmentService.getAttachment(attachmentId);

        return ResponseEntity.ok(new ApiResponse<>(true, attachment));
    }
}