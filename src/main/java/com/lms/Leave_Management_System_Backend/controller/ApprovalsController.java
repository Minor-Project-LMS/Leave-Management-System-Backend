package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.ApprovalDelegationRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    public ApprovalsController(LeaveRequestRepository leaveRequestRepository, UserRepository userRepository, ApprovalDelegationRepository delegationRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.delegationRepository = delegationRepository;
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
        
        Page<LeaveRequest> leaveRequests;
        
        // Check if user is currently acting as a delegate
        LocalDate today = LocalDate.now();
        
        // Get requests where user is the designated approver or active delegate
        // For now, get requests where user is the designated approver
        // This can be extended to include requests where user is the active delegate
        
        // Filter by current approver with status support
        if (status.equals("ALL")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverId(currentUser.getId(), pageable);
        } else if (status.equals("PENDING")) {
            // Include both PENDING_L1 and PENDING_L2 for managers and HR
            // Get all requests for current approver, then filter in memory
            Page<LeaveRequest> allRequests = leaveRequestRepository.findByCurrentApproverId(currentUser.getId(), pageable);
            leaveRequests = allRequests; // Keep as Page, will filter content later
        } else if (status.equals("APPROVED")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.APPROVED, pageable);
        } else if (status.equals("REJECTED")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.REJECTED, pageable);
        } else {
            leaveRequests = Page.empty();
        }

        List<LeaveRequestDto> dtos = leaveRequests.getContent().stream()
                .filter(lr -> {
                    if (status.equals("PENDING")) {
                        return lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 || 
                               lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L2;
                    }
                    return true;
                })
                .map(this::toLeaveRequestDto)
                .collect(Collectors.toList());

        // Calculate counts for all tabs (including PENDING_L2)
        Page<LeaveRequest> allRequests = leaveRequestRepository.findByCurrentApproverId(currentUser.getId(), Pageable.unpaged());
        long allCount = allRequests.getTotalElements();
        
        long pendingCount = allRequests.getContent().stream()
            .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 || 
                          lr.getStatus() == LeaveRequest.RequestStatus.PENDING_L2)
            .count();
        
        long approvedCount = allRequests.getContent().stream()
            .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.APPROVED)
            .count();
        
        long rejectedCount = allRequests.getContent().stream()
            .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.REJECTED)
            .count();

        ApprovalInboxResponse.ApprovalCounts counts = new ApprovalInboxResponse.ApprovalCounts(
            (int) allCount, (int) pendingCount, (int) approvedCount, (int) rejectedCount
        );

        ApprovalInboxResponse response = new ApprovalInboxResponse(
            page, // Return 1-based page number as per contract
            limit,
            leaveRequests.getTotalElements(),
            leaveRequests.getTotalPages(),
            counts,
            dtos
        );
        
        return ResponseEntity.ok(response);
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
}