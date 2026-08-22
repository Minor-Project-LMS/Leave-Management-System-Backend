package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/approvals")
public class ManagerApprovalsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;

    public ManagerApprovalsController(LeaveRequestRepository leaveRequestRepository, UserRepository userRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/pending")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<PendingApprovalPreview>> getPendingApprovals(
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {
        
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Simplified implementation - would query actual pending approvals
        List<LeaveRequest> pendingL1 = leaveRequestRepository.findByCurrentApproverIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L1);
        List<LeaveRequest> pendingL2 = leaveRequestRepository.findByCurrentApproverIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L2);
        
        List<PendingApprovalPreview> previews = new ArrayList<>();
        for (LeaveRequest request : pendingL1.stream().limit(limit).toList()) {
            PendingApprovalPreview preview = new PendingApprovalPreview();
            preview.setId("LR-" + request.getId());
            preview.setName(request.getUser().getName());
            preview.setType(request.getCategory().getName());
            preview.setDateRange(request.getStartDate() + " - " + request.getEndDate());
            preview.setDays(request.getTotalDays().doubleValue());
            previews.add(preview);
        }

        return ResponseEntity.ok(previews);
    }
}