package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveRequestsController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;

    public LeaveRequestsController(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            LeaveCategoryRepository leaveCategoryRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
    }

    @PostMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
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
        leaveRequest.setTotalDays(request.getTotalDays());
        leaveRequest.setReason(request.getReason());
        leaveRequest.setStatus(LeaveRequest.RequestStatus.DRAFT);
        leaveRequest.setAppliedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);
        
        return ResponseEntity.status(201).body(new ApiResponse<>(true, dto));
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveRequestDto>> listLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appliedAt,desc") String sort,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
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
                leaveRequests.getNumber(),
                leaveRequests.getSize(),
                leaveRequests.getTotalElements(),
                leaveRequests.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtos, pageResponse));
    }

    @GetMapping("/{requestId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
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
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
    }

    @PatchMapping("/{requestId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
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
        leaveRequest.setTotalDays(request.getTotalDays());
        leaveRequest.setReason(request.getReason());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);
        
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
    }

    @PostMapping("/{requestId}/submit")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
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

        // Set to pending and assign approver
        leaveRequest.setStatus(LeaveRequest.RequestStatus.PENDING_L1);
        if (leaveRequest.getUser().getReportsTo() != null) {
            leaveRequest.setCurrentApprover(leaveRequest.getUser().getReportsTo());
        }
        leaveRequest.setAppliedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LeaveRequestDto dto = toLeaveRequestDto(saved);
        
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
    }

    @PatchMapping("/{requestId}/withdraw")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<LeaveRequestDto>> withdrawLeaveRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) WithdrawRequest withdrawRequest,
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
        
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
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

    public static class WithdrawRequest {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
