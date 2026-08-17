package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveApprovalsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;

    public LeaveApprovalsController(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
    }

    @PatchMapping("/{requestId}/decisions")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<LeaveRequestDto>> makeDecision(
            @PathVariable Long requestId,
            @Valid @RequestBody LeaveDecisionRequest request,
            Authentication authentication) {
        
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check if request is in pending state
        if (leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L1 && 
            leaveRequest.getStatus() != LeaveRequest.RequestStatus.PENDING_L2) {
            throw new ConflictException("Only pending requests can be approved or rejected");
        }

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Check if user is the current approver
        if (leaveRequest.getCurrentApprover() == null || 
            !leaveRequest.getCurrentApprover().getId().equals(currentUser.getId())) {
            throw new SecurityException("You are not the current approver for this request");
        }

        // Process decision
        if ("APPROVE".equalsIgnoreCase(request.getDecision())) {
            if (leaveRequest.getStatus() == LeaveRequest.RequestStatus.PENDING_L1) {
                // Check if needs L2 approval (simplified - always approve for now)
                leaveRequest.setStatus(LeaveRequest.RequestStatus.APPROVED);
                leaveRequest.setCurrentApprover(null);
            } else {
                leaveRequest.setStatus(LeaveRequest.RequestStatus.APPROVED);
                leaveRequest.setCurrentApprover(null);
            }
        } else if ("REJECT".equalsIgnoreCase(request.getDecision())) {
            if (request.getComment() == null || request.getComment().trim().isEmpty()) {
                throw new BusinessRuleException("Comment is required for rejection");
            }
            leaveRequest.setStatus(LeaveRequest.RequestStatus.REJECTED);
            leaveRequest.setReason(leaveRequest.getReason() + " [Rejection: " + request.getComment() + "]");
            leaveRequest.setCurrentApprover(null);
        } else {
            throw new IllegalArgumentException("Invalid decision. Must be APPROVE or REJECT");
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);
        
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
    }

    @GetMapping("/pending-approvals")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveRequestDto>> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(page, size);
        
        var pendingL1 = leaveRequestRepository.findByCurrentApproverIdAndStatus(
                currentUser.getId(), 
                LeaveRequest.RequestStatus.PENDING_L1);
        
        var pendingL2 = leaveRequestRepository.findByCurrentApproverIdAndStatus(
                currentUser.getId(), 
                LeaveRequest.RequestStatus.PENDING_L2);
        
        pendingL1.addAll(pendingL2);
        
        var dtos = pendingL1.stream()
                .map(this::toLeaveRequestDto)
                .collect(java.util.stream.Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                page,
                size,
                pendingL1.size(),
                (int) Math.ceil((double) pendingL1.size() / size)
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtos, pageResponse));
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
