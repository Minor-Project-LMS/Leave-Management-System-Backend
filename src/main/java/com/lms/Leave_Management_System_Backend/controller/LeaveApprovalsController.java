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
@RequestMapping("/api/v1/leave-approvals")
public class LeaveApprovalsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;

    public LeaveApprovalsController(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
    }

    // This controller can be used for additional approval-related endpoints
    // The main decision endpoint is now in LeaveRequestsController to match the OpenAPI contract

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
