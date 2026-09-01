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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    
    // In-memory comment storage (comment table would be better for production)
    private static final Map<Long, List<CommentDto>> commentStorage = new ConcurrentHashMap<>();

    public LeaveRequestsController(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            LeaveApprovalRepository leaveApprovalRepository,
            ApprovalDelegationRepository delegationRepository,
            LeaveLedgerRepository leaveLedgerRepository, NotificationQueueRepository notificationQueueRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.delegationRepository = delegationRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.notificationQueueRepository = notificationQueueRepository;
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

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUser(user);
        leaveRequest.setCategory(leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId())));
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setSessionType(LeaveRequest.SessionType.valueOf(request.getSessionType()));

        // Calculate totalDays based on dates and session type
        BigDecimal calculatedDays = calculateTotalDays(request.getStartDate(), request.getEndDate(), request.getSessionType());
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
        
        // Contract uses 1-based page numbers, Spring uses 0-based
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
                page, // Return 1-based page number as per contract
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

        leaveRequest.setCategory(leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId())));
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setSessionType(LeaveRequest.SessionType.valueOf(request.getSessionType()));

        // Recalculate totalDays based on updated dates and session type
        BigDecimal calculatedDays = calculateTotalDays(request.getStartDate(), request.getEndDate(), request.getSessionType());
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
            // Check if there's an active delegation
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
            
            // Filter for overlapping dates (excluding current request)
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
        // Note: actingAsDelegateFor is temporarily disabled pending database migration
        // if (isDelegatedApprover) {
        //     approval.setActingAsDelegateFor(currentApprover);
        // }
        leaveApprovalRepository.save(approval);

        // Update the request status
        if ("APPROVED".equals(decisionRequest.getDecision())) {
            // Check if need HR approval based on days threshold
            if (leaveRequest.getTotalDays().compareTo(BigDecimal.valueOf(5)) > 0 &&
                leaveRequest.getStatus() == LeaveRequest.RequestStatus.PENDING_L1) {
                // Move to HR approval
                leaveRequest.setStatus(LeaveRequest.RequestStatus.PENDING_L2);
                // Set currentApprover to first HR admin (simplified)
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
            // Rejected
            leaveRequest.setStatus(LeaveRequest.RequestStatus.REJECTED);
            leaveRequest.setCurrentApprover(null);
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if ("APPROVED".equals(decisionRequest.getDecision())) {
            if (saved.getStatus() == LeaveRequest.RequestStatus.APPROVED) {
                // Final Approval Notification to Employee
                createNotification(
                        saved.getUser(),
                        "LEAVE_APPROVED",
                        "Leave Request Approved",
                        "Your leave request from " + saved.getStartDate() + " to " + saved.getEndDate() + " has been approved.",
                        "LEAVE_APPROVAL",
                        saved.getId()
                );
            } else if (saved.getStatus() == LeaveRequest.RequestStatus.PENDING_L2) {
                // HR Level Pending Notification to HR Admin
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
            // Rejection Notification to Employee
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

        // Check if can be withdrawn (only pending states)
        if (leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L1 &&
            leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L2) {
            throw new ConflictException("Only pending requests can be withdrawn");
        }

        // Check ownership
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

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
            !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view your own leave request approvals");
        }

        // Query actual approval history from leave_approvals table
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
        dto.setActingAsDelegateFor(null); // Temporarily null pending database migration
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

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") && 
            !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view your own leave request comments");
        }

        // Return comments from in-memory storage
        List<CommentDto> comments = commentStorage.getOrDefault(requestId, new ArrayList<>());
        
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/{requestId}/comments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long requestId,
            @RequestBody CommentRequest commentRequest,
            Authentication authentication) {
        
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") && 
            !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only comment on your own leave requests");
        }

        // Create and store comment
        CommentDto comment = new CommentDto();
        comment.setId(commentStorage.getOrDefault(requestId, new ArrayList<>()).size() + 1);
        comment.setRequestId(requestId);
        comment.setAuthorId(currentUser.getId());
        comment.setAuthorName(currentUser.getName());
        comment.setMessage(commentRequest.getMessage());
        comment.setCreatedAt(LocalDateTime.now());

        // Store in in-memory storage
        commentStorage.computeIfAbsent(requestId, k -> new ArrayList<>()).add(comment);

        return ResponseEntity.status(201).body(comment);
    }

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
            throw new SecurityException("You can only view your own leave request attachments");
        }

        // Simplified implementation - would query actual attachments
        List<AttachmentDto> attachments = List.of();

        return ResponseEntity.ok(attachments);
    }

    @PostMapping("/{requestId}/attachments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<AttachmentDto> uploadAttachment(
            @PathVariable Long requestId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            Authentication authentication) {
        
        // Check file size (10 MB limit as per OpenAPI spec)
        long maxSize = 10 * 1024 * 1024; // 10 MB in bytes
        if (file.getSize() > maxSize) {
            throw new BusinessRuleException("File size exceeds the 10 MB limit");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") && 
            !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only upload attachments to your own leave requests");
        }

        // Simplified implementation - would upload to storage service
        AttachmentDto attachment = new AttachmentDto();
        attachment.setId((int) System.currentTimeMillis());
        attachment.setFileName(file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedBy(currentUser.getId());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setDownloadUrl("/uploads/attachments/" + requestId + "/" + file.getOriginalFilename());

        return ResponseEntity.status(201).body(attachment);
    }

    @GetMapping("/{requestId}/pdf")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<?> downloadRequestPdf(
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
            throw new SecurityException("You can only download your own leave request PDFs");
        }

        // Simplified implementation - would generate actual PDF
        // In real implementation, return PDF file stream
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{requestId}/attachments/{attachmentId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<?> downloadAttachment(
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
            throw new SecurityException("You can only download your own leave request attachments");
        }

        // Simplified implementation - would return actual file stream
        // In real implementation, return file content with proper headers
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<?> exportLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "csv") String format,
            Authentication authentication) {

        // Simplified implementation - would generate actual export file
        // In real implementation, return CSV/XLSX file stream
        return ResponseEntity.ok().build();
    }

    private LeaveRequestDto toLeaveRequestDto(LeaveRequest request) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setId(request.getId());
        dto.setUserId(request.getUser().getId());
        dto.setUserName(request.getUser().getName());
        dto.setCategoryId(request.getCategory().getId());
        dto.setCategoryName(request.getCategory().getName());
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setSessionType(request.getSessionType().name());
        dto.setTotalDays(request.getTotalDays());
        dto.setReason(request.getReason());
        dto.setStatus(request.getStatus().name());
        if (request.getCurrentApprover() != null) {
            dto.setCurrentApproverId(request.getCurrentApprover().getId());
            dto.setCurrentApproverName(request.getCurrentApprover().getName());
        }
        dto.setAppliedAt(request.getAppliedAt());
        return dto;
    }

    private void createNotification(User recipient, String templateCode, String title, String message, String entityType, Long entityId) {
        NotificationQueue notification = new NotificationQueue();
        notification.setUser(recipient);
        notification.setChannel(NotificationQueue.Channel.IN_APP);
        notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
        notification.setTemplateCode(templateCode);
        notification.setRelatedEntityType(entityType);
        notification.setRelatedEntityId(entityId);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setIsRead(false);

        // Format payload as JSON so NotificationsController can parse title & description
        String payload = String.format("{\"title\":\"%s\",\"message\":\"%s\"}", title, message);
        notification.setPayload(payload);

        notificationQueueRepository.save(notification);
    }
}
