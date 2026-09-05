package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.exception.SecurityException;
import com.lms.Leave_Management_System_Backend.model.CompOffRequest;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.CompOffRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/comp-off-requests")
public class CompOffController {

    private final CompOffRequestRepository compOffRequestRepository;
    private final UserRepository userRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final AttachmentService attachmentService;

    public CompOffController(
            CompOffRequestRepository compOffRequestRepository,
            UserRepository userRepository,
            LeaveLedgerRepository leaveLedgerRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            AttachmentService attachmentService) {
        this.compOffRequestRepository = compOffRequestRepository;
        this.userRepository = userRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<CompOffRequestDto> createCompOffRequest(
            @Valid @RequestBody CompOffRequestCreate request,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Validate workedOn is a holiday or weekend (simplified for now)
        // In production, check against holiday calendar and weekend logic

        CompOffRequest compOffRequest = new CompOffRequest();
        compOffRequest.setUser(currentUser);
        compOffRequest.setWorkedOn(request.getWorkedOn());
        compOffRequest.setReason(request.getReason());
        compOffRequest.setHoursWorked(BigDecimal.valueOf(request.getHoursWorked()));
        
        // Derive daysCredited from hoursWorked per policy (typically 0.5 or 1.0)
        double daysCredited = request.getHoursWorked() >= 8 ? 1.0 : 0.5;
        compOffRequest.setDaysCredited(BigDecimal.valueOf(daysCredited));
        
        compOffRequest.setStatus(CompOffRequest.RequestStatus.PENDING);
        compOffRequest.setCreatedAt(LocalDateTime.now());

        // Set expiry date (e.g., 90 days from workedOn as per policy)
        compOffRequest.setExpiryDate(request.getWorkedOn().plusDays(90));

        // Set approver to user's manager
        if (currentUser.getReportsTo() != null) {
            compOffRequest.setApprover(currentUser.getReportsTo());
        }

        CompOffRequest saved = compOffRequestRepository.save(compOffRequest);
        CompOffRequestDto dto = toCompOffRequestDto(saved);
        
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<CompOffListResponse> listCompOffRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            Authentication authentication) {

        // If userId not provided, use current user for employees
        if (userId == null) {
            String email = authentication.getName();
            User currentUser = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", email));
            userId = currentUser.getId();
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

        if (statusEnum != null) {
            requests = compOffRequestRepository.findByUserIdAndStatus(userId, statusEnum, pageable);
        } else {
            requests = compOffRequestRepository.findByUserId(userId, pageable);
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
    public ResponseEntity<CompOffRequestDto> getCompOffRequest(
            @PathVariable Long compId) {
        
        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));
        
        CompOffRequestDto dto = toCompOffRequestDto(request);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{compId}/decisions")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<CompOffRequestDto> makeDecision(
            @PathVariable Long compId,
            @RequestBody CompOffDecisionRequest decisionRequest,
            Authentication authentication) {
        
        String email = authentication.getName();
        User approver = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        if (request.getStatus() != CompOffRequest.RequestStatus.PENDING) {
            throw new ConflictException("Only pending requests can be decided");
        }

        // Validate decision enum values match contract
        if (!"APPROVED".equals(decisionRequest.getDecision()) && !"REJECTED".equals(decisionRequest.getDecision())) {
            throw new ConflictException("Invalid decision. Must be APPROVED or REJECTED");
        }

        if ("APPROVED".equals(decisionRequest.getDecision())) {
            request.setStatus(CompOffRequest.RequestStatus.APPROVED);
            request.setApprover(approver);
            
            // Credit leave ledger with comp-off days
            int currentYear = Year.now().getValue();
            // Find or create leave ledger entry for comp-off category
            // Assuming comp-off category ID is known (e.g., 6 or similar)
            // For now, we'll use a generic approach
            
            // Credit the leave ledger
            // This is a simplified implementation - in production, you'd need to:
            // 1. Find the comp-off leave category
            // 2. Find or create the ledger entry for the user/year/category
            // 3. Update accrued and closing_balance
            
        } else if ("REJECTED".equals(decisionRequest.getDecision())) {
            request.setStatus(CompOffRequest.RequestStatus.REJECTED);
            request.setApprover(approver);
        }

        CompOffRequest saved = compOffRequestRepository.save(request);
        CompOffRequestDto dto = toCompOffRequestDto(saved);
        
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{compId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<Void> deleteCompOffRequest(
            @PathVariable Long compId,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        // Only allow withdrawal if PENDING
        if (request.getStatus() != CompOffRequest.RequestStatus.PENDING) {
            throw new ConflictException("Only pending requests can be withdrawn");
        }

        // Check ownership
        if (!request.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only withdraw your own comp-off requests");
        }

        compOffRequestRepository.delete(request);
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
        
        dto.setWorkedOn(request.getWorkedOn());
        dto.setReason(request.getReason());
        dto.setDaysCredited(request.getDaysCredited().doubleValue());
        dto.setExpiryDate(request.getExpiryDate());
        dto.setStatus(request.getStatus().name());
        
        if (request.getApprover() != null) {
            dto.setApproverId(request.getApprover().getId().intValue());
            dto.setApproverName(request.getApprover().getName());
        }
        
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
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

        AttachmentDto attachment = attachmentService.getAttachment(attachmentId);

        return ResponseEntity.ok(new ApiResponse<>(true, attachment));
    }
}