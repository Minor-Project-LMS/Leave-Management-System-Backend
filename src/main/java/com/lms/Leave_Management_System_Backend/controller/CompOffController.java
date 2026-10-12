package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import com.lms.Leave_Management_System_Backend.model.*;
import com.lms.Leave_Management_System_Backend.repository.*;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/comp-off-requests")
public class CompOffController {

    private final CompOffRequestRepository compOffRequestRepository;
    private final UserRepository userRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final NotificationQueueRepository notificationQueueRepository;
    private final AttachmentService attachmentService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ApprovalDelegationRepository delegationRepository;

    public CompOffController(
            CompOffRequestRepository compOffRequestRepository,
            UserRepository userRepository,
            LeaveLedgerRepository leaveLedgerRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            NotificationQueueRepository notificationQueueRepository,
            AttachmentService attachmentService,
            LeaveRequestRepository leaveRequestRepository,
            ApprovalDelegationRepository delegationRepository) {
        this.compOffRequestRepository = compOffRequestRepository;
        this.userRepository = userRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.notificationQueueRepository = notificationQueueRepository;
        this.attachmentService = attachmentService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.delegationRepository = delegationRepository;
    }

    @PostMapping
    @RequireRole({"MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<CompOffRequestDto> createCompOffRequest(
            @Valid @RequestBody CompOffRequestCreate request,
            Authentication authentication) {

        String email = authentication.getName();
        User issuer = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Get the target employee
        User targetEmployee = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        // Validate authorization: issuer must be the target's reporting manager or HR_ADMIN
        if (!isAuthorizedToGrant(issuer, targetEmployee)) {
            throw new SecurityException("You are not authorized to grant comp-off to this employee");
        }

        // Validate workedOn is a holiday or weekend (simplified for now)
        // In production, check against holiday calendar and weekend logic

        CompOffRequest compOffRequest = new CompOffRequest();
        compOffRequest.setUser(targetEmployee);
        compOffRequest.setWorkedOn(request.getWorkedOn());
        compOffRequest.setReason(request.getReason());
        compOffRequest.setHoursWorked(BigDecimal.valueOf(request.getHoursWorked()));

        // Derive daysCredited from hoursWorked per policy (typically 0.5 or 1.0)
        double daysCredited = request.getHoursWorked() >= 8 ? 1.0 : 0.5;
        compOffRequest.setDaysCredited(BigDecimal.valueOf(daysCredited));

        // Auto-set status to APPROVED when created by manager (new workflow)
        compOffRequest.setStatus(CompOffRequest.RequestStatus.APPROVED);
        compOffRequest.setCreatedAt(LocalDateTime.now());

        // Use the expiry date from the request
        compOffRequest.setExpiryDate(request.getExpiryDate());

        // Set issuer to the current user (manager/HR)
        compOffRequest.setIssuer(issuer);

        CompOffRequest saved = compOffRequestRepository.save(compOffRequest);

        // Credit the leave ledger with comp-off days
        creditLeaveLedger(targetEmployee, saved.getDaysCredited(), saved.getExpiryDate(), saved.getId());

        // Create notification for the employee
        createNotification(
                saved.getUser(),
                "COMP_OFF_GRANTED",
                "Comp-Off Credit Granted",
                "You have been granted " + saved.getDaysCredited() + " comp-off day(s) for " + saved.getWorkedOn() + ". Valid until " + saved.getExpiryDate() + ".",
                "COMP_OFF_GRANTED",
                saved.getId()
        );

        CompOffRequestDto dto = toCompOffRequestDto(saved);

        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<CompOffListResponse> listCompOffRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean hasBalance,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // If userId not provided, use current user for employees
        if (userId == null) {
            userId = currentUser.getId();
        } else {
            // For managers/HR, validate they can view the requested user's grants
            if (!currentUser.getRole().getRoleCode().equals("HR_ADMIN")) {
                Long finalUserId = userId;
                User targetUser = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", finalUserId));

                if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
                    // Check if target is a direct report
                    if (targetUser.getReportsTo() == null ||
                            !targetUser.getReportsTo().getId().equals(currentUser.getId())) {
                        throw new SecurityException("You can only view comp-off grants for your direct reports");
                    }
                } else {
                    // Employees can only view their own
                    if (!userId.equals(currentUser.getId())) {
                        throw new SecurityException("You can only view your own comp-off grants");
                    }
                }
            }
        }

        Pageable pageable = PageRequest.of(page - 1, limit,
                sort != null ? Sort.by(sort) : Sort.by("createdAt").descending());

        Page<CompOffRequest> requests;
        CompOffRequest.RequestStatus statusEnum = null;
        if (status != null) {
            try {
                statusEnum = CompOffRequest.RequestStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                // Invalid status, return empty page
                requests = Page.empty(pageable);
            }
        }

        LocalDate today = LocalDate.now();

        if (hasBalance != null && hasBalance) {
            requests = compOffRequestRepository.findWithFiltersAndBalance(
                    userId, statusEnum, hasBalance, today, pageable);
        } else {
            if (statusEnum != null) {
                requests = compOffRequestRepository.findByUserIdAndStatus(userId, statusEnum, pageable);
            } else {
                requests = compOffRequestRepository.findByUserId(userId, pageable);
            }
        }

        List<CompOffRequestDto> dtoList = requests.getContent().stream()
                .map(this::toCompOffRequestDto)
                .collect(Collectors.toList());

        // Create response matching contract: allOf PagedResponse + data
        CompOffListResponse response = new CompOffListResponse();
        response.setPage(page);
        response.setLimit(limit);
        response.setTotalCount(requests.getTotalElements());
        response.setTotalPages(requests.getTotalPages());
        response.setData(dtoList);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{compId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<CompOffRequestDetailDto> getCompOffRequest(
            @PathVariable Long compId,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Check access permissions
        if (!canAccessCompOffRequest(request, currentUser)) {
            throw new SecurityException("You are not authorized to view this comp-off request");
        }

        CompOffRequestDetailDto dto = toCompOffRequestDetailDto(request);

        // Load linked leave requests
        List<LeaveRequest> linkedRequests = leaveRequestRepository.findByCompOffRequestId(compId);
        dto.setLinkedLeaveRequests(linkedRequests.stream()
                .map(this::toLeaveRequestDto)
                .collect(Collectors.toList()));

        // Load attachments
        dto.setAttachments(attachmentService.listAttachments(
                Attachment.EntityType.COMP_OFF_REQUEST,
                compId
        ));

        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{compId}")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<Void> deleteCompOffRequest(
            @PathVariable Long compId,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Check authorization
        if (!isAuthorizedToRevoke(request, currentUser)) {
            throw new SecurityException("You are not authorized to revoke this comp-off grant");
        }

        // Validate status - can only revoke APPROVED grants
        if (request.getStatus() != CompOffRequest.RequestStatus.APPROVED) {
            throw new ConflictException("Only APPROVED comp-off grants can be revoked");
        }

        // Check if there are any pending or approved leave requests linked to this grant
        List<LeaveRequest> linkedRequests = leaveRequestRepository.findByCompOffRequestId(compId);
        boolean hasActiveClaims = linkedRequests.stream()
                .anyMatch(lr -> lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 ||
                        lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L2 ||
                        lr.getStatus() == LeaveRequest.RequestStatus.APPROVED);

        if (hasActiveClaims) {
            throw new ConflictException("Cannot revoke comp-off grant with active leave requests linked to it");
        }

        // Set status to REJECTED (revoked)
        request.setStatus(CompOffRequest.RequestStatus.REJECTED);
        compOffRequestRepository.save(request);

        // Create notification for the employee
        createNotification(
                request.getUser(),
                "COMP_OFF_REVOKED",
                "Comp-Off Grant Revoked",
                "Your comp-off grant for " + request.getWorkedOn() + " has been revoked by " + currentUser.getName() + ".",
                "COMP_OFF_REVOKED",
                request.getId()
        );

        return ResponseEntity.noContent().build();
    }

    private CompOffRequestDto toCompOffRequestDto(CompOffRequest request) {
        CompOffRequestDto dto = new CompOffRequestDto();
        dto.setId(request.getId().intValue());

        // Generate display ID (e.g., CO-2024-014)
        String displayId = "CO-" + request.getCreatedAt().getYear() + "-" +
                String.format("%03d", request.getId().intValue());
        dto.setDisplayId(displayId);

        dto.setUserId(request.getUser().getId().intValue());
        dto.setEmployeeName(request.getUser().getName());
        // Resolve user avatar URL
        dto.setUserAvatarUrl(attachmentService.resolveAvatarUrl(request.getUser().getId()));

        dto.setWorkedOn(request.getWorkedOn());
        dto.setReason(request.getReason());
        dto.setDaysCredited(request.getDaysCredited().doubleValue());
        dto.setExpiryDate(request.getExpiryDate());
        dto.setStatus(request.getStatus().name());

        // Calculate computed fields
        Double daysClaimed = leaveRequestRepository.sumDaysClaimedByCompOffRequestId(request.getId());
        Double daysPending = leaveRequestRepository.sumDaysPendingByCompOffRequestId(request.getId());
        dto.setDaysClaimed(daysClaimed != null ? daysClaimed : 0.0);
        dto.setDaysPending(daysPending != null ? daysPending : 0.0);
        dto.setDaysRemaining(request.getDaysCredited().doubleValue() -
                (dto.getDaysClaimed() + dto.getDaysPending()));

        if (request.getApprover() != null) {
            dto.setApproverId(request.getApprover().getId().intValue());
            dto.setApproverName(request.getApprover().getName());
            // Resolve approver avatar URL
            dto.setApproverAvatarUrl(attachmentService.resolveAvatarUrl(request.getApprover().getId()));
        }

        if (request.getIssuer() != null) {
            dto.setIssuerId(request.getIssuer().getId().intValue());
            dto.setIssuerName(request.getIssuer().getName());
            // Resolve issuer avatar URL
            dto.setIssuerAvatarUrl(attachmentService.resolveAvatarUrl(request.getIssuer().getId()));
        }

        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private CompOffRequestDetailDto toCompOffRequestDetailDto(CompOffRequest request) {
        CompOffRequestDetailDto dto = new CompOffRequestDetailDto();

        // Copy all fields from the base DTO
        dto.setId(request.getId().intValue());

        String displayId = "CO-" + request.getCreatedAt().getYear() + "-" +
                String.format("%03d", request.getId().intValue());
        dto.setDisplayId(displayId);

        dto.setUserId(request.getUser().getId().intValue());
        dto.setEmployeeName(request.getUser().getName());
        dto.setUserAvatarUrl(attachmentService.resolveAvatarUrl(request.getUser().getId()));

        dto.setWorkedOn(request.getWorkedOn());
        dto.setReason(request.getReason());
        dto.setDaysCredited(request.getDaysCredited().doubleValue());
        dto.setExpiryDate(request.getExpiryDate());
        dto.setStatus(request.getStatus().name());

        // Calculate computed fields
        Double daysClaimed = leaveRequestRepository.sumDaysClaimedByCompOffRequestId(request.getId());
        Double daysPending = leaveRequestRepository.sumDaysPendingByCompOffRequestId(request.getId());
        dto.setDaysClaimed(daysClaimed != null ? daysClaimed : 0.0);
        dto.setDaysPending(daysPending != null ? daysPending : 0.0);
        dto.setDaysRemaining(request.getDaysCredited().doubleValue() -
                (dto.getDaysClaimed() + dto.getDaysPending()));

        if (request.getApprover() != null) {
            dto.setApproverId(request.getApprover().getId().intValue());
            dto.setApproverName(request.getApprover().getName());
            dto.setApproverAvatarUrl(attachmentService.resolveAvatarUrl(request.getApprover().getId()));
        }

        if (request.getIssuer() != null) {
            dto.setIssuerId(request.getIssuer().getId().intValue());
            dto.setIssuerName(request.getIssuer().getName());
            dto.setIssuerAvatarUrl(attachmentService.resolveAvatarUrl(request.getIssuer().getId()));
        }

        dto.setCreatedAt(request.getCreatedAt());

        return dto;
    }

    private LeaveRequestDto toLeaveRequestDto(LeaveRequest leaveRequest) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setId(leaveRequest.getId());
        dto.setUserId(leaveRequest.getUser().getId());
        dto.setUserName(leaveRequest.getUser().getName());
        dto.setCategoryId(leaveRequest.getCategory().getId().intValue());
        dto.setCategoryName(leaveRequest.getCategory().getCategoryName());
        dto.setStartDate(leaveRequest.getStartDate());
        dto.setEndDate(leaveRequest.getEndDate());
        dto.setSessionType(leaveRequest.getSessionType().name());
        dto.setTotalDays(leaveRequest.getTotalDays());
        dto.setLopDays(leaveRequest.getLopDays());
        dto.setReason(leaveRequest.getReason());
        dto.setStatus(leaveRequest.getStatus().name());

        if (leaveRequest.getCurrentApprover() != null) {
            dto.setCurrentApproverId(leaveRequest.getCurrentApprover().getId());
            dto.setCurrentApproverName(leaveRequest.getCurrentApprover().getName());
            dto.setCurrentApproverAvatarUrl(attachmentService.resolveAvatarUrl(leaveRequest.getCurrentApprover().getId()));
        }

        dto.setUserAvatarUrl(attachmentService.resolveAvatarUrl(leaveRequest.getUser().getId()));

        if (leaveRequest.getCompOffRequest() != null) {
            dto.setCompOffRequestId(leaveRequest.getCompOffRequest().getId().intValue());
        }

        dto.setAppliedAt(leaveRequest.getAppliedAt());

        return dto;
    }

    private boolean isAuthorizedToGrant(User issuer, User targetEmployee) {
        // HR_ADMIN can grant to anyone
        if (issuer.getRole().getRoleCode().equals("HR_ADMIN")) {
            return true;
        }

        // MANAGER can grant to their direct reports
        if (issuer.getRole().getRoleCode().equals("MANAGER")) {
            // Check if issuer is the target's reporting manager
            if (targetEmployee.getReportsTo() != null &&
                    targetEmployee.getReportsTo().getId().equals(issuer.getId())) {
                return true;
            }

            // Check for active delegation
            List<ApprovalDelegation> activeDelegations = delegationRepository
                    .findActiveDelegationsForDelegatorOnDate(issuer.getId(), LocalDate.now());

            for (ApprovalDelegation delegation : activeDelegations) {
                if (delegation.getDelegate().getId().equals(targetEmployee.getReportsTo() != null ?
                        targetEmployee.getReportsTo().getId() : null)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isAuthorizedToRevoke(CompOffRequest request, User revoker) {
        // HR_ADMIN can revoke any grant
        if (revoker.getRole().getRoleCode().equals("HR_ADMIN")) {
            return true;
        }

        // MANAGER can revoke grants they issued or to their direct reports
        if (revoker.getRole().getRoleCode().equals("MANAGER")) {
            // If they issued it
            if (request.getIssuer() != null && request.getIssuer().getId().equals(revoker.getId())) {
                return true;
            }

            // If they are the reporting manager
            if (request.getUser().getReportsTo() != null &&
                    request.getUser().getReportsTo().getId().equals(revoker.getId())) {
                return true;
            }

            // Check for active delegation
            List<ApprovalDelegation> activeDelegations = delegationRepository
                    .findActiveDelegationsForDelegatorOnDate(revoker.getId(), LocalDate.now());

            for (ApprovalDelegation delegation : activeDelegations) {
                if (delegation.getDelegate().getId().equals(request.getUser().getReportsTo() != null ?
                        request.getUser().getReportsTo().getId() : null)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canAccessCompOffRequest(CompOffRequest request, User currentUser) {
        // HR_ADMIN can see all
        if (currentUser.getRole().getRoleCode().equals("HR_ADMIN")) {
            return true;
        }

        // Employee can see their own
        if (request.getUser().getId().equals(currentUser.getId())) {
            return true;
        }

        // Manager can see their direct reports' grants
        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            if (request.getUser().getReportsTo() != null &&
                    request.getUser().getReportsTo().getId().equals(currentUser.getId())) {
                return true;
            }
        }

        return false;
    }

    private void creditLeaveLedger(User user, BigDecimal daysCredited, LocalDate expiryDate, Long compOffRequestId) {
        // Find the comp-off leave category
        // Try to find by category code first, then by name
        Optional<com.lms.Leave_Management_System_Backend.model.LeaveCategory> compOffCategory =
                leaveCategoryRepository.findByCategoryCode("COMP_OFF");

        if (compOffCategory.isEmpty()) {
            // Try to find by name if category code doesn't exist
            compOffCategory = leaveCategoryRepository.findAll().stream()
                    .filter(c -> "Compensatory Off".equalsIgnoreCase(c.getCategoryName()) ||
                            "Comp Off".equalsIgnoreCase(c.getCategoryName()) ||
                            "Comp-Off".equalsIgnoreCase(c.getCategoryName()))
                    .findFirst();
        }

        if (compOffCategory.isEmpty()) {
            throw new ResourceNotFoundException("LeaveCategory", "Comp-Off category not found");
        }

        com.lms.Leave_Management_System_Backend.model.LeaveCategory category = compOffCategory.get();

        // Determine fiscal year based on expiry date
        int fiscalYear = expiryDate.getYear();

        // Find or create ledger entry
        Optional<LeaveLedger> existingLedger = leaveLedgerRepository
                .findByUserIdAndCategoryIdAndFiscalYear(user.getId(), category.getId(), fiscalYear);

        LeaveLedger ledger;
        if (existingLedger.isPresent()) {
            ledger = existingLedger.get();
        } else {
            // Create new ledger entry
            ledger = new LeaveLedger();
            ledger.setUser(user);
            ledger.setCategory(category);
            ledger.setFiscalYear(fiscalYear);
            ledger.setOpeningBalance(BigDecimal.ZERO);
            ledger.setAccrued(BigDecimal.ZERO);
            ledger.setUsed(BigDecimal.ZERO);
            ledger.setEncashed(BigDecimal.ZERO);
            ledger.setCarriedForward(BigDecimal.ZERO);
            ledger.setClosingBalance(BigDecimal.ZERO);
        }

        // Update the ledger with the comp-off credit
        BigDecimal newAccrued = ledger.getAccrued().add(daysCredited);
        ledger.setAccrued(newAccrued);
        ledger.setClosingBalance(ledger.getClosingBalance().add(daysCredited));
        ledger.setTransactionDate(LocalDate.now());
        ledger.setTransactionType("CREDIT");
        ledger.setReferenceType("COMP_OFF_REQUEST");
        ledger.setReferenceId(compOffRequestId);
        ledger.setDescription("Comp-off credit granted - Valid until " + expiryDate);

        leaveLedgerRepository.save(ledger);
    }

    // ============================================================
    // ATTACHMENT ENDPOINTS
    // ============================================================

    @GetMapping("/{compId}/attachments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<List<AttachmentDto>> getAttachments(
            @PathVariable Long compId,
            Authentication authentication) {

        CompOffRequest compOffRequest = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !compOffRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view attachments for your own comp-off requests");
        }

        // For managers, verify they are the current approver
        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            if (compOffRequest.getApprover() == null ||
                    !compOffRequest.getApprover().getId().equals(currentUser.getId())) {
                throw new SecurityException("You can only view attachments for requests where you are the approver");
            }
        }

        List<AttachmentDto> attachments = attachmentService.listAttachments(
                com.lms.Leave_Management_System_Backend.model.Attachment.EntityType.COMP_OFF_REQUEST,
                compId
        );

        return ResponseEntity.ok(attachments);
    }

    @PostMapping("/{compId}/attachments/init-upload")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentInitUploadResponse>> initAttachmentUpload(
            @PathVariable Long compId,
            @Valid @RequestBody AttachmentInitUploadRequest request,
            Authentication authentication) {

        CompOffRequest compOffRequest = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Check access permissions - only the request owner can upload
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!compOffRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only upload attachments to your own comp-off requests");
        }

        AttachmentInitUploadResponse response = attachmentService.initializeUpload(
                com.lms.Leave_Management_System_Backend.model.Attachment.EntityType.COMP_OFF_REQUEST,
                compId,
                request,
                currentUser.getId()
        );

        return ResponseEntity.status(201).body(new ApiResponse<>(true, response));
    }

    @PostMapping("/{compId}/attachments/{attachmentId}/confirm")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentDto>> confirmAttachmentUpload(
            @PathVariable Long compId,
            @PathVariable Long attachmentId,
            @RequestBody(required = false) AttachmentConfirmRequest confirmRequest,
            Authentication authentication) {

        CompOffRequest compOffRequest = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Check access permissions - only the request owner can confirm
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!compOffRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only confirm attachments for your own comp-off requests");
        }

        AttachmentDto attachment = attachmentService.confirmUpload(attachmentId, confirmRequest);

        return ResponseEntity.ok(new ApiResponse<>(true, attachment));
    }

    @GetMapping("/{compId}/attachments/{attachmentId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentDto>> getAttachment(
            @PathVariable Long compId,
            @PathVariable Long attachmentId,
            Authentication authentication) {

        CompOffRequest compOffRequest = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") &&
                !compOffRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only view attachments for your own comp-off requests");
        }

        // For managers, verify they are the current approver
        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            if (compOffRequest.getApprover() == null ||
                    !compOffRequest.getApprover().getId().equals(currentUser.getId())) {
                throw new SecurityException("You can only view attachments for requests where you are the approver");
            }
        }

        AttachmentDto attachment = attachmentService.getAttachment(attachmentId);

        return ResponseEntity.ok(new ApiResponse<>(true, attachment));
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
}