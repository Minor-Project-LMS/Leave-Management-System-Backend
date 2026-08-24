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
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));
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

    @PostMapping("/{requestId}/withdraw")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<LeaveRequestDto>> withdrawLeaveRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) com.lms.Leave_Management_System_Backend.dto.WithdrawRequest withdrawRequest,
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

    @GetMapping("/{requestId}/approvals")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<CommentDto>> getApprovals(
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
            throw new SecurityException("You can only view your own leave request approvals");
        }

        // Simplified implementation - would query actual approval history
        List<CommentDto> approvals = List.of(
                new CommentDto(1L, requestId, leaveRequest.getCurrentApprover() != null ? leaveRequest.getCurrentApprover().getId() : 0L, 
                              leaveRequest.getCurrentApprover() != null ? leaveRequest.getCurrentApprover().getName() : "Pending", 
                              "Pending approval", LocalDateTime.now())
        );

        return ResponseEntity.ok(approvals);
    }

    @GetMapping("/{requestId}/comments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<CommentDto>> getComments(
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
            throw new SecurityException("You can only view your own leave request comments");
        }

        // Simplified implementation - would query actual comments
        List<CommentDto> comments = List.of();

        return ResponseEntity.ok(comments);
    }

    @PostMapping("/{requestId}/comments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long requestId,
            @RequestBody CommentRequest commentRequest,
            Authentication authentication) {
        
        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") && 
            !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only comment on your own leave requests");
        }

        // Simplified implementation - would save to database
        CommentDto comment = new CommentDto();
        comment.setId((int) System.currentTimeMillis());
        comment.setRequestId(requestId);
        comment.setAuthorId(currentUser.getId());
        comment.setAuthorName(currentUser.getName());
        comment.setMessage(commentRequest.getMessage());
        comment.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.status(201).body(comment);
    }

    @GetMapping("/{requestId}/attachments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
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
            throw new SecurityException("You can only view your own leave request attachments");
        }

        // Simplified implementation - would query actual attachments
        List<AttachmentDto> attachments = List.of();

        return ResponseEntity.ok(attachments);
    }

    @PostMapping("/{requestId}/attachments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<AttachmentDto> uploadAttachment(
            @PathVariable Long requestId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            Authentication authentication) {
        
        // Check file size (10 MB limit as per OpenAPI spec)
        long maxSize = 10 * 1024 * 1024; // 10 MB in bytes
        if (file.getSize() > maxSize) {
            throw new BusinessRuleException("File size exceeds the 10 MB limit");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", requestId));

        // Check access permissions
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (currentUser.getRole().getRoleCode().equals("EMPLOYEE") && 
            !leaveRequest.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only upload attachments to your own leave requests");
        }

        // Simplified implementation - would upload to storage service
        AttachmentDto attachment = new AttachmentDto();
        attachment.setId((int) System.currentTimeMillis());
        attachment.setFileName(file.getOriginalFilename());
        attachment.setContentType(file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setUploadedBy(currentUser.getId());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setDownloadUrl("/uploads/attachments/" + requestId + "/" + file.getOriginalFilename());

        return ResponseEntity.status(201).body(attachment);
    }

    @GetMapping("/{requestId}/pdf")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<?> downloadRequestPdf(
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
            throw new SecurityException("You can only download your own leave request PDFs");
        }

        // Simplified implementation - would generate actual PDF
        // In real implementation, return PDF file stream
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<?> exportLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "csv") String format,
            Authentication authentication) {

        // Simplified implementation - would generate actual export file
        // In real implementation, return CSV/XLSX file stream
        return ResponseEntity.ok().build();
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
