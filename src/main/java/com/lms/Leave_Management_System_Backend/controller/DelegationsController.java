package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.ApprovalDelegation;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.ApprovalDelegationRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/delegations")
public class DelegationsController {

    private final ApprovalDelegationRepository delegationRepository;
    private final UserRepository userRepository;

    public DelegationsController(ApprovalDelegationRepository delegationRepository, UserRepository userRepository) {
        this.delegationRepository = delegationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<DelegationDto>> listDelegations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long delegatorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // If delegatorId not provided, use current user for managers
        if (delegatorId == null && currentUser.getRole().getRoleCode().equals("MANAGER")) {
            delegatorId = currentUser.getId();
        }

        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<ApprovalDelegation> delegations;

        if (delegatorId != null) {
            delegations = delegationRepository.findByDelegatorId(delegatorId.longValue(), pageable);
        } else {
            delegations = delegationRepository.findAll(pageable);
        }

        // Filter by status if provided
        List<DelegationDto> dtoList = delegations.getContent().stream()
                .filter(delegation -> status == null || matchesStatus(delegation, status))
                .map(this::toDelegationDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                page,
                limit,
                delegations.getTotalElements(),
                delegations.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtoList, pageResponse));
    }

    @PostMapping
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<DelegationDto> createDelegation(
            @RequestBody DelegationInputDto request,
            Authentication authentication) {
        
        String email = authentication.getName();
        User delegator = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        User delegate = userRepository.findById(request.getDelegateId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getDelegateId()));

        // Check for overlapping delegations
        if (delegationRepository.findOverlappingDelegations(delegator.getId(), request.getStartDate(), request.getEndDate()).isPresent()) {
            throw new IllegalStateException("Overlapping active delegation already exists for this delegator/date range");
        }

        ApprovalDelegation delegation = new ApprovalDelegation();
        delegation.setDelegator(delegator);
        delegation.setDelegate(delegate);
        delegation.setStartDate(request.getStartDate());
        delegation.setEndDate(request.getEndDate());
        delegation.setActive(true);
        delegation.setCreatedAt(LocalDateTime.now());

        ApprovalDelegation saved = delegationRepository.save(delegation);
        return ResponseEntity.status(201).body(toDelegationDto(saved));
    }

    @GetMapping("/{delegationId}")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<DelegationDto> getDelegation(
            @PathVariable Long delegationId) {
        
        ApprovalDelegation delegation = delegationRepository.findById(delegationId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Delegation", delegationId));

        return ResponseEntity.ok(toDelegationDto(delegation));
    }

    @PatchMapping("/{delegationId}")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<DelegationDto> updateDelegation(
            @PathVariable Long delegationId,
            @RequestBody DelegationInputDto request,
            Authentication authentication) {
        
        ApprovalDelegation delegation = delegationRepository.findById(delegationId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Delegation", delegationId));

        User delegate = userRepository.findById(request.getDelegateId().longValue())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getDelegateId()));

        delegation.setDelegate(delegate);
        delegation.setStartDate(request.getStartDate());
        delegation.setEndDate(request.getEndDate());

        ApprovalDelegation saved = delegationRepository.save(delegation);
        return ResponseEntity.ok(toDelegationDto(saved));
    }

    @PostMapping("/{delegationId}/revoke")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<DelegationDto> revokeDelegation(
            @PathVariable Long delegationId,
            Authentication authentication) {
        
        ApprovalDelegation delegation = delegationRepository.findById(delegationId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Delegation", delegationId));

        delegation.setActive(false);
        delegation.setEndDate(LocalDate.now()); // End delegation early

        ApprovalDelegation saved = delegationRepository.save(delegation);
        return ResponseEntity.ok(toDelegationDto(saved));
    }

    private boolean matchesStatus(ApprovalDelegation delegation, String status) {
        LocalDate today = LocalDate.now();
        String computedStatus;

        if (!delegation.isActive()) {
            computedStatus = "REVOKED";
        } else if (today.isBefore(delegation.getStartDate())) {
            computedStatus = "UPCOMING";
        } else if (today.isAfter(delegation.getEndDate())) {
            computedStatus = "PAST";
        } else {
            computedStatus = "ACTIVE";
        }

        return computedStatus.equals(status);
    }

    private DelegationDto toDelegationDto(ApprovalDelegation delegation) {
        DelegationDto dto = new DelegationDto();
        dto.setDelegationId(delegation.getId());
        
        UserDto delegatorDto = new UserDto();
        delegatorDto.setId(delegation.getDelegator().getId());
        delegatorDto.setName(delegation.getDelegator().getName());
        dto.setDelegator(delegatorDto);
        
        UserDto delegateDto = new UserDto();
        delegateDto.setId(delegation.getDelegate().getId());
        delegateDto.setName(delegation.getDelegate().getName());
        dto.setDelegate(delegateDto);
        
        dto.setStartDate(delegation.getStartDate());
        dto.setEndDate(delegation.getEndDate());
        dto.setIsActive(delegation.isActive());
        dto.setCreatedAt(delegation.getCreatedAt());
        
        // Set scope
        DelegationDto.DelegationScope scope = new DelegationDto.DelegationScope();
        scope.setAllTypes(true); // Default to all types for now
        dto.setScope(scope);
        
        return dto;
    }
}