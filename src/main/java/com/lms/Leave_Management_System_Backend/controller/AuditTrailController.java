package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.AuditLogEntry;
import com.lms.Leave_Management_System_Backend.dto.AuditLogEntryDetail;
import com.lms.Leave_Management_System_Backend.dto.PaginatedResponse;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import com.lms.Leave_Management_System_Backend.repository.AuditTrailRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditTrailController {

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @GetMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<AuditLogEntry>> searchAuditLog(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("performedAt").descending());
        
        Page<AuditTrail> auditTrailPage;
        
        // Build query based on filters using specification
        if (dateFrom != null || dateTo != null || userId != null || action != null || entityType != null || q != null) {
            auditTrailPage = auditTrailRepository.findAll((root, query, cb) -> {
                var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                
                if (dateFrom != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("performedAt"), LocalDateTime.parse(dateFrom)));
                }
                if (dateTo != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("performedAt"), LocalDateTime.parse(dateTo)));
                }
                if (userId != null) {
                    predicates.add(cb.equal(root.get("performedBy").get("id"), userId.longValue()));
                }
                if (action != null) {
                    predicates.add(cb.equal(root.get("action"), AuditTrail.AuditAction.valueOf(action)));
                }
                if (entityType != null) {
                    predicates.add(cb.equal(root.get("entityType"), entityType));
                }
                if (q != null) {
                    predicates.add(cb.or(
                        cb.like(root.get("entityType"), "%" + q + "%"),
                        cb.like(root.get("action").as(String.class), "%" + q + "%")
                    ));
                }
                
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }, pageable);
        } else {
            auditTrailPage = auditTrailRepository.findAll(pageable);
        }
        
        List<AuditLogEntry> entries = auditTrailPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(page, limit, (int) auditTrailPage.getTotalElements(), auditTrailPage.getTotalPages());
        
        return ResponseEntity.ok(new PaginatedResponse<>(true, entries, pageResponse));
    }

    @GetMapping("/recent")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<AuditLogEntry>> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(0, limit, Sort.by("performedAt").descending());
        
        Page<AuditTrail> recentEntries = auditTrailRepository.findAll(pageable);
        
        List<AuditLogEntry> entries = recentEntries.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(entries);
    }

    @GetMapping("/{auditId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<AuditLogEntryDetail> getAuditLogEntry(
            @PathVariable Long auditId,
            Authentication authentication) {

        AuditTrail auditTrail = auditTrailRepository.findById(auditId)
                .orElseThrow(() -> new RuntimeException("Audit log entry not found"));

        AuditLogEntryDetail entryDetail = new AuditLogEntryDetail();
        entryDetail.setId(auditId.intValue());
        entryDetail.setEntityType(auditTrail.getEntityType());
        entryDetail.setEntityId(auditTrail.getEntityId() != null ? auditTrail.getEntityId().intValue() : null);
        entryDetail.setAction(auditTrail.getAction().name());
        entryDetail.setPerformedBy(auditTrail.getPerformedBy().getId().intValue());
        entryDetail.setPerformedByName(auditTrail.getPerformedBy().getName());
        entryDetail.setDescription(auditTrail.getAction().name() + " operation on " + auditTrail.getEntityType());
        entryDetail.setModule("System"); // Could be derived from entity type
        entryDetail.setStatus("SUCCESS"); // Could be derived from action or added to entity
        entryDetail.setIpAddress(auditTrail.getIpAddress());
        entryDetail.setPerformedAt(auditTrail.getPerformedAt());
        
        // Parse JSON states
        try {
            if (auditTrail.getBeforeState() != null) {
                entryDetail.setBeforeState(Map.of("state", auditTrail.getBeforeState()));
            }
            if (auditTrail.getAfterState() != null) {
                entryDetail.setAfterState(Map.of("state", auditTrail.getAfterState()));
            }
        } catch (Exception e) {
            // Handle JSON parsing errors
            entryDetail.setBeforeState(Map.of());
            entryDetail.setAfterState(Map.of());
        }

        return ResponseEntity.ok(entryDetail);
    }

    @GetMapping("/export")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<?> exportAuditLog(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "csv") String format,
            Authentication authentication) {

        // Simplified implementation - would generate actual export file
        // In real implementation, return CSV/XLSX file stream
        return ResponseEntity.ok().build();
    }

    // Helper method to convert AuditTrail to AuditLogEntry
    private AuditLogEntry convertToDto(AuditTrail auditTrail) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setId(auditTrail.getId().intValue());
        entry.setEntityType(auditTrail.getEntityType());
        entry.setEntityId(auditTrail.getEntityId().intValue());
        entry.setAction(auditTrail.getAction().name());
        entry.setPerformedBy(auditTrail.getPerformedBy().getId().intValue());
        entry.setPerformedByName(auditTrail.getPerformedBy().getName());
        entry.setDescription(auditTrail.getAction().name() + " operation on " + auditTrail.getEntityType());
        entry.setModule("System"); // Could be derived from entity type
        entry.setStatus("SUCCESS"); // Could be derived from action or added to entity
        entry.setIpAddress(auditTrail.getIpAddress());
        entry.setPerformedAt(auditTrail.getPerformedAt());
        return entry;
    }
}