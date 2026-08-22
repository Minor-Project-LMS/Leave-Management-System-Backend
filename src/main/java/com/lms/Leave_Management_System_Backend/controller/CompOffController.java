package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.CompOffRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.CompOffRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/comp-off-requests")
public class CompOffController {

    private final CompOffRequestRepository compOffRequestRepository;
    private final UserRepository userRepository;

    public CompOffController(
            CompOffRequestRepository compOffRequestRepository,
            UserRepository userRepository) {
        this.compOffRequestRepository = compOffRequestRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<CompOffRequestDto>> createCompOffRequest(
            @RequestBody CompOffRequestCreate request,
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
        
        if (request.getHoursWorked() != null) {
            compOffRequest.setHoursWorked(BigDecimal.valueOf(request.getHoursWorked()));
        }
        
        compOffRequest.setDaysCredited(BigDecimal.valueOf(request.getDaysCredited()));
        compOffRequest.setStatus(CompOffRequest.RequestStatus.PENDING);
        compOffRequest.setCreatedAt(LocalDate.now());

        // Set expiry date (e.g., 90 days from workedOn as per policy)
        compOffRequest.setExpiryDate(request.getWorkedOn().plusDays(90));

        CompOffRequest saved = compOffRequestRepository.save(compOffRequest);
        CompOffRequestDto dto = toCompOffRequestDto(saved);
        
        return ResponseEntity.status(201).body(new ApiResponse<>(true, dto));
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<CompOffRequestDto>> listCompOffRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        
        // If userId not provided, use current user for employees
        if (userId == null) {
            String email = authentication.getName();
            User currentUser = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", email));
            userId = currentUser.getId();
        }

        Pageable pageable = PageRequest.of(page, size, 
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

        PageResponse pageResponse = new PageResponse(
            requests.getNumber(),
            requests.getSize(),
            requests.getTotalElements(),
            requests.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtoList, pageResponse));
    }

    @GetMapping("/{compId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<CompOffRequestDto>> getCompOffRequest(
            @PathVariable Long compId) {
        
        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));
        
        CompOffRequestDto dto = toCompOffRequestDto(request);
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
    }

    @PatchMapping("/{compId}/decisions")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<CompOffRequestDto>> makeDecision(
            @PathVariable Long compId,
            @RequestBody LeaveDecisionRequest decisionRequest,
            Authentication authentication) {
        
        String email = authentication.getName();
        User approver = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        CompOffRequest request = compOffRequestRepository.findById(compId)
                .orElseThrow(() -> new ResourceNotFoundException("CompOffRequest", compId));

        if (request.getStatus() != CompOffRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be decided");
        }

        String decision = decisionRequest.getDecision();
        if ("APPROVED".equalsIgnoreCase(decision)) {
            request.setStatus(CompOffRequest.RequestStatus.APPROVED);
            request.setApprover(approver);
            // In production: Credit leave ledger here
        } else if ("REJECTED".equalsIgnoreCase(decision)) {
            request.setStatus(CompOffRequest.RequestStatus.REJECTED);
            request.setApprover(approver);
        } else {
            throw new IllegalArgumentException("Invalid decision: " + decision);
        }

        CompOffRequest saved = compOffRequestRepository.save(request);
        CompOffRequestDto dto = toCompOffRequestDto(saved);
        
        return ResponseEntity.ok(new ApiResponse<>(true, dto));
    }

    private CompOffRequestDto toCompOffRequestDto(CompOffRequest request) {
        CompOffRequestDto dto = new CompOffRequestDto();
        dto.setCompId(request.getId().intValue());
        
        UserDto employeeDto = new UserDto();
        employeeDto.setId(request.getUser().getId());
        employeeDto.setName(request.getUser().getName());
        employeeDto.setEmail(request.getUser().getEmail());
        dto.setEmployee(employeeDto);
        
        dto.setWorkedOn(request.getWorkedOn());
        dto.setReason(request.getReason());
        dto.setDaysCredited(request.getDaysCredited().doubleValue());
        dto.setExpiryDate(request.getExpiryDate());
        dto.setStatus(request.getStatus().name());
        
        if (request.getApprover() != null) {
            UserDto approverDto = new UserDto();
            approverDto.setId(request.getApprover().getId());
            approverDto.setName(request.getApprover().getName());
            dto.setApprover(approverDto);
        }
        
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }
}