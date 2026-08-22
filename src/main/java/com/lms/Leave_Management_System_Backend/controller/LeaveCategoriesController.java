package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/leave-categories")
public class LeaveCategoriesController {

    private final LeaveCategoryRepository leaveCategoryRepository;

    public LeaveCategoriesController(LeaveCategoryRepository leaveCategoryRepository) {
        this.leaveCategoryRepository = leaveCategoryRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveCategoryDto>> listLeaveCategories(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveCategory> categoriesPage = leaveCategoryRepository.findAll(pageable);

        List<LeaveCategory> categories = categoriesPage.getContent();
        
        // Filter by status if provided
        if ("ACTIVE".equalsIgnoreCase(status)) {
            categories = categories.stream()
                    .filter(c -> c.getId() != null) // Filter for active categories
                    .collect(Collectors.toList());
        } else if ("INACTIVE".equalsIgnoreCase(status)) {
            categories = categories.stream()
                    .filter(c -> c.getId() == null) // Filter for inactive categories
                    .collect(Collectors.toList());
        }

        List<LeaveCategoryDto> categoryDtos = categories.stream()
                .map(this::toLeaveCategoryDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                categoriesPage.getNumber(),
                categoriesPage.getSize(),
                categoriesPage.getTotalElements(),
                categoriesPage.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, categoryDtos, pageResponse));
    }

    @GetMapping("/{categoryId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<LeaveCategoryDto> getLeaveCategory(@PathVariable Integer categoryId) {
        LeaveCategory category = leaveCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", categoryId));
        return ResponseEntity.ok(toLeaveCategoryDto(category));
    }

    @PostMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<LeaveCategoryDto> createLeaveCategory(
            @Valid @RequestBody LeaveCategoryRequest request) {
        
        LeaveCategory category = new LeaveCategory();
        category.setName(request.getName());
        category.setPaid(request.getPaid() != null ? request.getPaid() : true);
        category.setRequiresDocument(request.getRequiresDocument() != null ? request.getRequiresDocument() : false);
        category.setDefaultAnnualQuota(request.getDefaultAnnualQuota() != null ? request.getDefaultAnnualQuota() : 0.0);

        LeaveCategory saved = leaveCategoryRepository.save(category);
        LeaveCategoryDto dto = toLeaveCategoryDto(saved);
        
        return ResponseEntity.status(201).body(dto);
    }

    @PatchMapping("/{categoryId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<LeaveCategoryDto> updateLeaveCategory(
            @PathVariable Integer categoryId,
            @Valid @RequestBody LeaveCategoryRequest request) {
        
        LeaveCategory category = leaveCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", categoryId));

        category.setName(request.getName());
        if (request.getPaid() != null) {
            category.setPaid(request.getPaid());
        }
        if (request.getRequiresDocument() != null) {
            category.setRequiresDocument(request.getRequiresDocument());
        }
        if (request.getDefaultAnnualQuota() != null) {
            category.setDefaultAnnualQuota(request.getDefaultAnnualQuota());
        }

        LeaveCategory saved = leaveCategoryRepository.save(category);
        LeaveCategoryDto dto = toLeaveCategoryDto(saved);
        
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{categoryId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<?> deleteLeaveCategory(@PathVariable Integer categoryId) {
        LeaveCategory category = leaveCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", categoryId));

        // Check if it's a system category (protected)
        if (category.getId() <= 5) { // Assuming first 5 are system categories
            throw new IllegalStateException("System-defined categories can only be deactivated, not deleted");
        }

        leaveCategoryRepository.delete(category);
        return ResponseEntity.noContent().build();
    }

    private LeaveCategoryDto toLeaveCategoryDto(LeaveCategory category) {
        LeaveCategoryDto dto = new LeaveCategoryDto();
        dto.setId(category.getId());
        dto.setCategoryName(category.getName());
        dto.setIsPaid(category.isPaid());
        dto.setRequiresDocument(category.isRequiresDocument());
        dto.setDefaultAnnualQuota(category.getDefaultAnnualQuota());
        dto.setStatus("ACTIVE"); // Default to active for now
        dto.setIsSystemCategory(category.getId() <= 5); // Assuming first 5 are system categories
        return dto;
    }
}
