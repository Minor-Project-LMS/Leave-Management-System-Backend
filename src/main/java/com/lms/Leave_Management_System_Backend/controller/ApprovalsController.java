package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;

    public ApprovalsController(LeaveRequestRepository leaveRequestRepository, UserRepository userRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/inbox")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveRequestDto>> getApprovalInbox(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        Sort.Direction direction = sort.equals("newest") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "appliedAt"));
        
        Page<LeaveRequest> leaveRequests;
        
        // Filter by current approver
        if (status.equals("ALL")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverId(currentUser.getId(), pageable);
        } else if (status.equals("PENDING")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.PENDING_L1, pageable);
        } else if (status.equals("APPROVED")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.APPROVED, pageable);
        } else if (status.equals("REJECTED")) {
            leaveRequests = leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.REJECTED, pageable);
        } else {
            leaveRequests = Page.empty();
        }

        List<LeaveRequestDto> dtos = leaveRequests.getContent().stream()
                .map(this::toLeaveRequestDto)
                .collect(Collectors.toList());

        // Calculate counts for all tabs
        Map<String, Integer> counts = new HashMap<>();
        counts.put("all", (int) leaveRequestRepository.findByCurrentApproverId(currentUser.getId(), Pageable.unpaged()).getTotalElements());
        counts.put("pending", (int) leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.PENDING_L1, Pageable.unpaged()).getTotalElements());
        counts.put("approved", (int) leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.APPROVED, Pageable.unpaged()).getTotalElements());
        counts.put("rejected", (int) leaveRequestRepository.findByCurrentApproverIdAndStatus(currentUser.getId(), LeaveRequest.RequestStatus.REJECTED, Pageable.unpaged()).getTotalElements());

        PageResponse pageResponse = new PageResponse(
                leaveRequests.getNumber(),
                leaveRequests.getSize(),
                leaveRequests.getTotalElements(),
                leaveRequests.getTotalPages()
        );

        // Create a custom response that includes counts
        PaginatedResponse<LeaveRequestDto> response = new PaginatedResponse<>(true, dtos, pageResponse);
        // Note: In a real implementation, you'd extend PaginatedResponse to include counts
        
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