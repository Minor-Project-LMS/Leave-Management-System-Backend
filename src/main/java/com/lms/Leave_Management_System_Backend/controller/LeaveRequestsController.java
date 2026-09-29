package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import com.lms.Leave_Management_System_Backend.model.*;
import com.lms.Leave_Management_System_Backend.model.LeaveApproval;
import com.lms.Leave_Management_System_Backend.repository.*;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
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
    private final com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService leaveLedgerProvisioningService;
    private final com.lms.Leave_Management_System_Backend.repository.CompOffRequestRepository compOffRequestRepository;

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
            AttachmentService attachmentService,
            LeaveLedgerProvisioningService leaveLedgerProvisioningService,
            com.lms.Leave_Management_System_Backend.repository.CompOffRequestRepository compOffRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.delegationRepository = delegationRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.notificationQueueRepository = notificationQueueRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.attachmentService = attachmentService;
        this.leaveLedgerProvisioningService = leaveLedgerProvisioningService;
        this.compOffRequestRepository = compOffRequestRepository;
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

        // Check for comp-off date conflicts
        validateCompOffDateConflict(user, request.getStartDate(), request.getEndDate());

        // Validate comp-off grant exists and is APPROVED if this is a comp-off claim
        // Note: Balance validation happens during approval, not at submission
        if (request.getCompOffRequestId() != null) {
            validateCompOffGrantExists(user, request.getCompOffRequestId(), request.getCategoryId(), request.getEndDate());
        }

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
            // This is where a request actually starts competing for
            // approval, so this is where LOP gets calculated and
            // overlapping pending/approved requests get rejected — a
            // DRAFT save skips all of this since it isn't submitted yet.
            applyPendingSubmissionChecks(leaveRequest, user);

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

        // Send notification to employee
        createNotification(
                saved.getUser(),
                "LEAVE_SUBMITTED",
                "Leave Request Submitted",
                "Your leave request for " + saved.getTotalDays() + " day(s) has been submitted.",
                "LEAVE_REQUEST",
                saved.getId()
        );

        // Send notification to manager for approval
        if (saved.getCurrentApprover() != null) {
            createNotification(
                    saved.getCurrentApprover(),
                    "LEAVE_APPROVAL_PENDING",
                    "Leave Approval Required",
                    "A leave request from " + saved.getUser().getName() + " for " + saved.getTotalDays() + " day(s) requires your approval.",
                    "LEAVE_APPROVAL",
                    saved.getId()
            );
        }

        LeaveRequestDto dto = toLeaveRequestDto(saved);

        return ResponseEntity.status(201).body(new ApiResponse<LeaveRequestDto>(true, dto));
    }

    /**
     * Calculate total days for a leave request under the Sandwich Leave
     * policy — see notes below.
     */
    // Sandwich Leave policy: when a leave request spans a weekend (start
    // date before the weekend, end date after it, in one continuous
    // request), the weekend days count as leave too — they're "sandwiched"
    // between two leave days, rather than being free days off in the
    // middle of a leave stretch. In practice, for a single continuous
    // date range this just means every calendar day in the range counts.
    private BigDecimal calculateTotalDays(LocalDate startDate, LocalDate endDate, String sessionType) {
        long calendarDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // Adjust for session type
        if ("FIRST_HALF".equals(sessionType) || "SECOND_HALF".equals(sessionType)) {
            return BigDecimal.valueOf(calendarDays * 0.5);
        } else {
            return BigDecimal.valueOf(calendarDays);
        }
    }

    private boolean datesOverlap(LeaveRequest a, LeaveRequest b) {
        return !(a.getEndDate().isBefore(b.getStartDate()) || a.getStartDate().isAfter(b.getEndDate()));
    }

    // Runs everything a leave request needs once it's actually competing
    // for approval (as opposed to sitting as a draft): computes the
    // Loss-of-Pay split against the current balance, and rejects it if it
    // overlaps another pending or approved request for the same employee.
    // Shared by createLeaveRequest() (the normal "Apply Leave" submit path)
    // and submitLeaveRequest() (converting an existing draft to pending),
    // so the two can't drift out of sync the way they previously did.
    private void applyPendingSubmissionChecks(LeaveRequest leaveRequest, User currentUser) {
        int currentYear = java.time.Year.now().getValue();
        leaveLedgerProvisioningService.getOrInitializeLedger(currentUser, currentYear);
        Optional<LeaveLedger> ledger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                leaveRequest.getUser().getId(),
                leaveRequest.getCategory().getId(),
                currentYear
        );

        BigDecimal availableBalance = ledger.map(LeaveLedger::getClosingBalance).orElse(BigDecimal.ZERO);
        BigDecimal lopDays = leaveRequest.getTotalDays().subtract(availableBalance);
        leaveRequest.setLopDays(lopDays.compareTo(BigDecimal.ZERO) > 0 ? lopDays : BigDecimal.ZERO);

        // Check for an overlapping request already in flight — either a
        // pending one awaiting approval, or one that's already approved.
        // Checking these separately gives a clearer message: pending vs.
        // approved mean different things to the employee.
        List<LeaveRequest> overlappingPending = leaveRequestRepository.findByUserIdAndStatusIn(
                leaveRequest.getUser().getId(),
                List.of(LeaveRequest.RequestStatus.PENDING_L1, LeaveRequest.RequestStatus.PENDING_L2)
        );
        boolean hasPendingOverlap = overlappingPending.stream()
                .filter(existing -> leaveRequest.getId() == null || !existing.getId().equals(leaveRequest.getId()))
                .anyMatch(existing -> datesOverlap(existing, leaveRequest));

        if (hasPendingOverlap) {
            throw new ConflictException("You have already submitted a leave request that is pending approval for this date.");
        }

        // Check for overlapping approved requests
        List<LeaveRequest> overlappingApproved = leaveRequestRepository.findByUserIdAndStatus(
                leaveRequest.getUser().getId(),
                LeaveRequest.RequestStatus.APPROVED
        );

        boolean hasOverlap = overlappingApproved.stream()
                .filter(existing -> leaveRequest.getId() == null || !existing.getId().equals(leaveRequest.getId()))
                .anyMatch(existing -> datesOverlap(existing, leaveRequest));

        if (hasOverlap) {
            throw new ConflictException("Your leave request is already approved for this date.");
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

        applyPendingSubmissionChecks(leaveRequest, currentUser);

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

        // Note: the overlapping-approved-leave check intentionally lives
        // only in submitLeaveRequest() (employee-side), not here. It's a
        // restriction on what an employee can submit, not a gate on what
        // an approver can approve — a request that made it to the
        // approval queue already passed that check once, and re-enforcing
        // it here just blocks legitimate approvals (e.g. an earlier
        // approved request that's since been cancelled/withdrawn) for no
        // benefit.

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
                // Validate comp-off balance before final approval
                validateCompOffBalance(leaveRequest);

                // Final approval - update leave ledger
                int currentYear = java.time.Year.now().getValue();
                // The ledger row for this category/year might not exist yet
                // (e.g. nobody has opened the Leave Ledger page for this
                // employee this year) — provisioning it here, rather than
                // silently skipping the deduction when it's missing, is
                // what actually made balances update on approval.
                leaveLedgerProvisioningService.getOrInitializeLedger(leaveRequest.getUser(), currentYear);
                Optional<LeaveLedger> ledger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                        leaveRequest.getUser().getId(),
                        leaveRequest.getCategory().getId(),
                        currentYear
                );

                if (ledger.isPresent()) {
                    LeaveLedger leaveLedger = ledger.get();
                    // Re-check against the current balance at approval time
                    // (it may have shifted since submission) and recompute
                    // the LOP split, rather than blocking the approval
                    // outright when balance is short.
                    BigDecimal availableBalance = leaveLedger.getClosingBalance();
                    BigDecimal lopDays = leaveRequest.getTotalDays().subtract(availableBalance);
                    lopDays = lopDays.compareTo(BigDecimal.ZERO) > 0 ? lopDays : BigDecimal.ZERO;
                    leaveRequest.setLopDays(lopDays);

                    BigDecimal paidDays = leaveRequest.getTotalDays().subtract(lopDays);
                    leaveLedger.setUsed(leaveLedger.getUsed().add(paidDays));
                    leaveLedger.setClosingBalance(leaveLedger.getClosingBalance().subtract(paidDays));
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
        // Resolve approver avatar URL
        dto.setApproverAvatarUrl(attachmentService.resolveAvatarUrl(approval.getApprover().getId()));
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

    @PostMapping("/{requestId}/comments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long requestId,
            @Valid @RequestBody CommentRequest commentRequest,
            Authentication authentication) {

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only add comments to your own leave requests");
        }

        List<CommentDto> comments = commentStorage.computeIfAbsent(requestId, k -> new ArrayList<>());

        CommentDto newComment = new CommentDto();
        newComment.setId(comments.size() + 1);
        newComment.setRequestId(requestId);
        newComment.setAuthorId(currentUser.getId());
        newComment.setAuthorName(currentUser.getName());
        newComment.setMessage(commentRequest.getMessage());
        newComment.setCreatedAt(LocalDateTime.now());

        comments.add(newComment);

        return ResponseEntity.status(201).body(newComment);
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

    /**
     * Helper method to validate comp-off date conflicts
     * Note: This validation is disabled in the new comp-off workflow.
     * Employees can now apply for regular leave even if they have comp-off grants.
     * They should use the comp-off category to claim against their grants.
     */
    private void validateCompOffDateConflict(User user, LocalDate startDate, LocalDate endDate) {
        // No-op in the new workflow - employees can have both regular leave and comp-off grants
    }

    private void validateCompOffGrantExists(User user, Integer compOffRequestId, Integer categoryId, LocalDate endDate) {
        // Find the comp-off grant
        CompOffRequest compOffRequest = compOffRequestRepository.findById(compOffRequestId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compOffRequestId));

        // Verify the grant belongs to the authenticated employee
        if (!compOffRequest.getUser().getId().equals(user.getId())) {
            throw new SecurityException("You can only claim against your own comp-off grants");
        }

        // Verify the grant is in APPROVED status
        if (compOffRequest.getStatus() != CompOffRequest.RequestStatus.APPROVED) {
            throw new ConflictException("Cannot claim against a comp-off grant that is not APPROVED");
        }

        // Verify the leave category is the Comp Off category
        if (!isCompOffCategory(categoryId)) {
            throw new ConflictException("compOffRequestId can only be used with the Comp Off leave category");
        }

        // Verify endDate is on or before the grant's expiry date
        if (endDate.isAfter(compOffRequest.getExpiryDate())) {
            throw new ConflictException("Leave request end date cannot be after the comp-off grant's expiry date");
        }
    }

    private void validateCompOffBalance(LeaveRequest leaveRequest) {
        if (leaveRequest.getCompOffRequest() == null) {
            return; // Not a comp-off claim, skip validation
        }

        CompOffRequest compOffRequest = leaveRequest.getCompOffRequest();

        // Calculate days remaining (excluding this current request)
        Double daysClaimed = leaveRequestRepository.sumDaysClaimedByCompOffRequestId(compOffRequest.getId());
        Double daysPending = leaveRequestRepository.sumDaysPendingByCompOffRequestId(compOffRequest.getId());
        
        // Subtract this request's days from pending if it's already counted
        double currentRequestDays = leaveRequest.getTotalDays().doubleValue();
        if (leaveRequest.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 || 
            leaveRequest.getStatus() == LeaveRequest.RequestStatus.PENDING_L2) {
            daysPending = (daysPending != null ? daysPending : 0.0) - currentRequestDays;
        }
        
        double totalClaimed = (daysClaimed != null ? daysClaimed : 0.0) + (daysPending > 0 ? daysPending : 0.0);
        double daysRemaining = compOffRequest.getDaysCredited().doubleValue() - totalClaimed;

        // Check if requested days exceed remaining balance
        if (currentRequestDays > daysRemaining) {
            throw new ConflictException("INSUFFICIENT_COMP_OFF_BALANCE");
        }
    }

    private boolean isCompOffCategory(Integer categoryId) {
        // Check if the category is the Comp Off category by name or code
        LeaveCategory category = leaveCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", categoryId));
        
        return "COMP_OFF".equalsIgnoreCase(category.getCategoryCode()) ||
               "Compensatory Off".equalsIgnoreCase(category.getCategoryName()) ||
               "Comp Off".equalsIgnoreCase(category.getCategoryName()) ||
               "Comp-Off".equalsIgnoreCase(category.getCategoryName());
    }

    private void createNotification(User user, String type, String title, String message, String entityType, Long entityId) {
        try {

            // Create notification for IN_APP channel
            NotificationQueue inAppNotification = new NotificationQueue();
            inAppNotification.setUser(user);
            inAppNotification.setChannel(NotificationQueue.Channel.IN_APP);
            inAppNotification.setTemplateCode(type);
            inAppNotification.setPayload("{\"title\":\"" + title + "\",\"message\":\"" + message + "\"}");
            inAppNotification.setRelatedEntityType(entityType);
            inAppNotification.setRelatedEntityId(entityId);
            inAppNotification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
            inAppNotification.setCreatedAt(LocalDateTime.now());
            inAppNotification.setScheduledAt(LocalDateTime.now());
            inAppNotification.setIsRead(false);
            notificationQueueRepository.save(inAppNotification);

            // Create notification for EMAIL channel
            NotificationQueue emailNotification = new NotificationQueue();
            emailNotification.setUser(user);
            emailNotification.setChannel(NotificationQueue.Channel.EMAIL);
            emailNotification.setTemplateCode(type);
            emailNotification.setPayload("{\"title\":\"" + title + "\",\"message\":\"" + message + "\"}");
            emailNotification.setRelatedEntityType(entityType);
            emailNotification.setRelatedEntityId(entityId);
            emailNotification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
            emailNotification.setCreatedAt(LocalDateTime.now());
            emailNotification.setScheduledAt(LocalDateTime.now());
            notificationQueueRepository.save(emailNotification);

        } catch (Exception e) {
            // Log error but don't fail the main operation
            e.printStackTrace();
        }
    }

    private LeaveRequestDto toLeaveRequestDto(LeaveRequest request) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setId(request.getId());
        if (request.getUser() != null) {
            dto.setUserId(request.getUser().getId());
            dto.setUserName(request.getUser().getName());
            // Resolve user avatar URL
            dto.setUserAvatarUrl(attachmentService.resolveAvatarUrl(request.getUser().getId()));
        }
        if (request.getCategory() != null) {
            dto.setCategoryId(request.getCategory().getId());
            dto.setCategoryName(request.getCategory().getName());
        }
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setSessionType(request.getSessionType() != null ? request.getSessionType().name() : null);
        dto.setTotalDays(request.getTotalDays());
        dto.setLopDays(request.getLopDays());
        dto.setReason(request.getReason());
        dto.setStatus(request.getStatus() != null ? request.getStatus().name() : null);
        if (request.getCurrentApprover() != null) {
            dto.setCurrentApproverId(request.getCurrentApprover().getId());
            dto.setCurrentApproverName(request.getCurrentApprover().getName());
            // Resolve current approver avatar URL
            dto.setCurrentApproverAvatarUrl(attachmentService.resolveAvatarUrl(request.getCurrentApprover().getId()));
        }
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

        // For managers, verify they are the current approver (or delegated approver)
        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            if (leaveRequest.getCurrentApprover() == null ||
                    !leaveRequest.getCurrentApprover().getId().equals(currentUser.getId())) {
                throw new SecurityException("You can only view attachments for requests where you are the current approver");
            }
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

        // For managers, verify they are the current approver (or delegated approver)
        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            if (leaveRequest.getCurrentApprover() == null ||
                    !leaveRequest.getCurrentApprover().getId().equals(currentUser.getId())) {
                throw new SecurityException("You can only view attachments for requests where you are the current approver");
            }
        }

        AttachmentDto attachment = attachmentService.getAttachment(attachmentId);

        return ResponseEntity.ok(new ApiResponse<>(true, attachment));
    }
}