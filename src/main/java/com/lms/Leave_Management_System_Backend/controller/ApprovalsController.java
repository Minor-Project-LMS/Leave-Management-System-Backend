package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveApproval;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.ApprovalDelegationRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveApprovalRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.LeaveEscalationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final ApprovalDelegationRepository delegationRepository;
    private final LeaveApprovalRepository leaveApprovalRepository;
    private final LeaveEscalationService leaveEscalationService;

    public ApprovalsController(LeaveRequestRepository leaveRequestRepository, UserRepository userRepository, ApprovalDelegationRepository delegationRepository, LeaveApprovalRepository leaveApprovalRepository, LeaveEscalationService leaveEscalationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.delegationRepository = delegationRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.leaveEscalationService = leaveEscalationService;
    }

    @GetMapping("/inbox")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApprovalInboxResponse> getApprovalInbox(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "newest") String sort,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        Sort.Direction direction = sort.equals("newest") ? Sort.Direction.DESC : Sort.Direction.ASC;
        // Contract uses 1-based page numbers, Spring uses 0-based
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(direction, "appliedAt"));

        // Get all requests where user is the current approver OR has made an approval decision
        List<LeaveApproval> userApprovals = leaveApprovalRepository.findByApproverId(currentUser.getId());
        List<Long> requestIdsWithUserAction = userApprovals.stream()
                .map(approval -> approval.getRequest().getId())
                .distinct()
                .collect(Collectors.toList());

        // Get requests where user is current approver
        Page<LeaveRequest> currentApproverRequests = leaveRequestRepository.findByCurrentApproverId(currentUser.getId(), Pageable.unpaged());
        List<Long> currentApproverRequestIds = currentApproverRequests.getContent().stream()
                .map(LeaveRequest::getId)
                .collect(Collectors.toList());

        // Requests that escalated away from this user — they're no longer
        // the current approver and never made a decision, so without this
        // they'd vanish from this screen entirely the moment it reassigns.
        List<LeaveRequest> escalatedAwayRequests = leaveRequestRepository.findByOriginalApproverId(currentUser.getId());
        List<Long> escalatedAwayRequestIds = escalatedAwayRequests.stream()
                .map(LeaveRequest::getId)
                .collect(Collectors.toList());

        // Combine all sets - all requests the manager should see
        List<Long> allAccessibleRequestIds = requestIdsWithUserAction;
        allAccessibleRequestIds.addAll(currentApproverRequestIds);
        allAccessibleRequestIds.addAll(escalatedAwayRequestIds);
        allAccessibleRequestIds = allAccessibleRequestIds.stream().distinct().collect(Collectors.toList());

        // Get all accessible requests using custom query with entity graph
        List<LeaveRequest> allAccessibleRequests = leaveRequestRepository.findByIds(allAccessibleRequestIds);

        // Filter by status for the current page
        List<LeaveRequest> filteredRequests = allAccessibleRequests.stream()
                .filter(lr -> {
                    if (status.equals("ALL")) {
                        return true;
                    } else if (status.equals("PENDING")) {
                        return lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 ||
                                lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L2;
                    } else if (status.equals("APPROVED")) {
                        return lr.getStatus() == LeaveRequest.RequestStatus.APPROVED;
                    } else if (status.equals("REJECTED")) {
                        return lr.getStatus() == LeaveRequest.RequestStatus.REJECTED;
                    } else if (status.equals("ESCALATED")) {
                        return lr.isEscalated();
                    }
                    return false;
                })
                // Pending requests HR has flagged to the manager always go
                // on top, ahead of the chosen date order.
                .sorted((a, b) -> {
                    boolean aFlagged = isPendingHrNotified(a);
                    boolean bFlagged = isPendingHrNotified(b);
                    if (aFlagged != bFlagged) {
                        return aFlagged ? -1 : 1;
                    }
                    if (direction == Sort.Direction.DESC) {
                        return b.getAppliedAt().compareTo(a.getAppliedAt());
                    } else {
                        return a.getAppliedAt().compareTo(b.getAppliedAt());
                    }
                })
                .collect(Collectors.toList());

        // Apply pagination manually since we're working with a list
        int startIndex = (page - 1) * limit;
        int endIndex = Math.min(startIndex + limit, filteredRequests.size());

        List<LeaveRequest> paginatedRequests;
        if (startIndex >= filteredRequests.size()) {
            paginatedRequests = List.of(); // Return empty list if page is out of bounds
        } else {
            paginatedRequests = filteredRequests.subList(startIndex, endIndex);
        }

        LocalDate urgentUntil = leaveEscalationService.urgentUntil();
        List<LeaveRequestDto> dtos = paginatedRequests.stream()
                .map(lr -> toLeaveRequestDto(lr, urgentUntil))
                .collect(Collectors.toList());

        // Calculate counts for all tabs
        long allCount = allAccessibleRequests.size();

        long pendingCount = allAccessibleRequests.stream()
                .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 ||
                        lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L2)
                .count();

        long approvedCount = allAccessibleRequests.stream()
                .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.APPROVED)
                .count();

        long rejectedCount = allAccessibleRequests.stream()
                .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.REJECTED)
                .count();

        long escalatedCount = allAccessibleRequests.stream()
                .filter(LeaveRequest::isEscalated)
                .count();

        ApprovalInboxResponse.ApprovalCounts counts = new ApprovalInboxResponse.ApprovalCounts(
                (int) allCount, (int) pendingCount, (int) approvedCount, (int) rejectedCount, (int) escalatedCount
        );

        int totalPages = (int) Math.ceil((double) filteredRequests.size() / limit);

        ApprovalInboxResponse response = new ApprovalInboxResponse(
                page, // Return 1-based page number as per contract
                limit,
                filteredRequests.size(),
                totalPages,
                counts,
                dtos
        );

        return ResponseEntity.ok(response);
    }

    // Requests HR has notified this user about and that are still waiting
    // on them — the frontend shows these as a popup on the dashboard.
    @GetMapping("/hr-alerts")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveRequestDto>> getHrAlerts(Authentication authentication) {
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        LocalDate urgentUntil = leaveEscalationService.urgentUntil();
        List<LeaveRequestDto> alerts = leaveRequestRepository
                .findByCurrentApproverIdAndStatusInAndHrNotifiedAtIsNotNullOrderByStartDateAsc(
                        currentUser.getId(),
                        List.of(LeaveRequest.RequestStatus.PENDING_L1, LeaveRequest.RequestStatus.PENDING_L2))
                .stream()
                .map(lr -> toLeaveRequestDto(lr, urgentUntil))
                .collect(Collectors.toList());

        return ResponseEntity.ok(alerts);
    }

    // HR notifies the current approver about a pending request — the step
    // that has to happen before an urgent request can be escalated.
    @PostMapping("/{requestId}/notify-manager")
    @RequireRole({"HR_ADMIN"})
    @Transactional
    public ResponseEntity<LeaveRequestDto> notifyManager(
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) NotifyManagerRequest body,
            Authentication authentication) {
        String email = authentication.getName();
        User hrUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        leaveEscalationService.notifyManager(request, hrUser, body != null ? body.getMessage() : null);
        return ResponseEntity.ok(toLeaveRequestDto(request, leaveEscalationService.urgentUntil()));
    }

    private static boolean isPendingHrNotified(LeaveRequest lr) {
        return lr.isHrNotified() && (lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L1
                || lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L2);
    }

    private LeaveRequestDto toLeaveRequestDto(LeaveRequest request, LocalDate urgentUntil) {
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
        dto.setLopDays(request.getLopDays());
        dto.setReason(request.getReason());
        dto.setStatus(request.getStatus().name());
        if (request.getCurrentApprover() != null) {
            dto.setCurrentApproverId(request.getCurrentApprover().getId());
            dto.setCurrentApproverName(request.getCurrentApprover().getName());
        }
        dto.setAppliedAt(request.getAppliedAt());
        dto.setEscalated(request.isEscalated());
        dto.setEscalatedAt(request.getEscalatedAt());
        dto.setOriginalApproverName(request.getOriginalApprover() != null ? request.getOriginalApprover().getName() : null);
        dto.setHrNotified(request.isHrNotified());
        dto.setHrNotifiedAt(request.getHrNotifiedAt());
        if (request.isHrNotified()) {
            dto.setHrNotifiedByName(request.getHrNotifiedBy() != null ? request.getHrNotifiedBy().getName() : "HR");
        }
        dto.setHrNotificationMessage(request.getHrNotificationMessage());
        dto.setUrgent(LeaveEscalationService.isUrgent(request, urgentUntil));
        return dto;
    }
}