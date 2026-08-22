package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.AuditLogEntry;
import com.lms.Leave_Management_System_Backend.dto.AuditLogEntryDetail;
import com.lms.Leave_Management_System_Backend.dto.PaginatedResponse;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
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

@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditTrailController {

    @GetMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<AuditLogEntry>> searchAuditLog(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Simplified implementation - would query actual audit log
        Pageable pageable = PageRequest.of(page, size, Sort.by("performedAt").descending());
        
        // Mock data for demonstration
        List<AuditLogEntry> entries = List.of(
                createAuditLogEntry(1, "LEAVE_REQUEST", 101, "CREATE", 1, "John Doe", 
                        "Leave request created", "Leave Requests", "SUCCESS", "192.168.1.1", LocalDateTime.now()),
                createAuditLogEntry(2, "LEAVE_REQUEST", 102, "APPROVE", 2, "Jane Smith", 
                        "Leave request approved", "Leave Requests", "SUCCESS", "192.168.1.2", LocalDateTime.now()),
                createAuditLogEntry(3, "USER", 5, "UPDATE", 1, "John Doe", 
                        "User profile updated", "User Management", "SUCCESS", "192.168.1.1", LocalDateTime.now())
        );

        PageResponse pageResponse = new PageResponse(page, size, entries.size(), 1);
        
        return ResponseEntity.ok(new PaginatedResponse<>(true, entries, pageResponse));
    }

    @GetMapping("/recent")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<AuditLogEntry>> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {

        // Simplified implementation - would query actual recent activity
        List<AuditLogEntry> recentEntries = List.of(
                createAuditLogEntry(1, "LEAVE_REQUEST", 101, "CREATE", 1, "John Doe", 
                        "Leave request created", "Leave Requests", "SUCCESS", "192.168.1.1", LocalDateTime.now()),
                createAuditLogEntry(2, "LEAVE_REQUEST", 102, "APPROVE", 2, "Jane Smith", 
                        "Leave request approved", "Leave Requests", "SUCCESS", "192.168.1.2", LocalDateTime.now())
        );

        return ResponseEntity.ok(recentEntries);
    }

    @GetMapping("/{auditId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<AuditLogEntryDetail> getAuditLogEntry(
            @PathVariable Integer auditId,
            Authentication authentication) {

        // Simplified implementation - would query actual audit log entry with details
        AuditLogEntryDetail entryDetail = new AuditLogEntryDetail();
        entryDetail.setId(auditId);
        entryDetail.setEntityType("LEAVE_REQUEST");
        entryDetail.setEntityId(101);
        entryDetail.setAction("CREATE");
        entryDetail.setPerformedBy(1);
        entryDetail.setPerformedByName("John Doe");
        entryDetail.setDescription("Leave request created");
        entryDetail.setModule("Leave Requests");
        entryDetail.setStatus("SUCCESS");
        entryDetail.setIpAddress("192.168.1.1");
        entryDetail.setPerformedAt(LocalDateTime.now());
        
        // Mock before/after states
        entryDetail.setBeforeState(Map.of("status", "DRAFT"));
        entryDetail.setAfterState(Map.of("status", "PENDING_L1"));

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

    // Helper method
    private AuditLogEntry createAuditLogEntry(Integer id, String entityType, Integer entityId, 
                                               String action, Integer performedBy, String performedByName,
                                               String description, String module, String status, 
                                               String ipAddress, LocalDateTime performedAt) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setId(id);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setPerformedBy(performedBy);
        entry.setPerformedByName(performedByName);
        entry.setDescription(description);
        entry.setModule(module);
        entry.setStatus(status);
        entry.setIpAddress(ipAddress);
        entry.setPerformedAt(performedAt);
        return entry;
    }
}