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
    private final LeaveApprovalRepository leaveApprovalRepository;

    public ApprovalsController(LeaveRequestRepository leaveRequestRepository, UserRepository userRepository, ApprovalDelegationRepository delegationRepository, LeaveApprovalRepository leaveApprovalRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.delegationRepository = delegationRepository;
        this.leaveApprovalRepository = leaveApprovalRepository;
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
        
        // Combine both sets - all requests the manager should see
        List<Long> allAccessibleRequestIds = requestIdsWithUserAction;
        allAccessibleRequestIds.addAll(currentApproverRequestIds);
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
                    }
                    return false;
                })
                .sorted((a, b) -> {
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
        
        List<LeaveRequestDto> dtos = paginatedRequests.stream()
                .map(this::toLeaveRequestDto)
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

        ApprovalInboxResponse.ApprovalCounts counts = new ApprovalInboxResponse.ApprovalCounts(
            (int) allCount, (int) pendingCount, (int) approvedCount, (int) rejectedCount
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