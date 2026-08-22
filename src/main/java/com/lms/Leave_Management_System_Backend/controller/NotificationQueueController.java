package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.NotificationQueueItem;
import com.lms.Leave_Management_System_Backend.dto.PaginatedResponse;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notification-queue")
public class NotificationQueueController {

    @GetMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<NotificationQueueItem>> getNotificationQueue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Simplified implementation - would query actual notification queue
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        // Mock data for demonstration
        List<NotificationQueueItem> queueItems = List.of(
                createNotificationQueueItem(1, 1, "John Doe", "LEAVE_APPROVED", 
                        "Your leave request has been approved", "EMAIL", "SENT", 
                        "LEAVE_REQUEST", 101, 0, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()),
                createNotificationQueueItem(2, 2, "Jane Smith", "LEAVE_SUBMITTED", 
                        "New leave request submitted", "IN_APP", "QUEUED", 
                        "LEAVE_REQUEST", 102, 0, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()),
                createNotificationQueueItem(3, 3, "Mike Johnson", "LEAVE_REJECTED", 
                        "Your leave request has been rejected", "EMAIL", "FAILED", 
                        "LEAVE_REQUEST", 103, 2, LocalDateTime.now().minusHours(2), null, null)
        );

        PageResponse pageResponse = new PageResponse(page, size, queueItems.size(), 1);
        
        return ResponseEntity.ok(new PaginatedResponse<>(true, queueItems, pageResponse));
    }

    @PostMapping("/{notificationId}/retry")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<NotificationQueueItem> retryNotification(
            @PathVariable Integer notificationId,
            Authentication authentication) {

        // Simplified implementation - would retry actual notification
        NotificationQueueItem item = createNotificationQueueItem(notificationId.intValue(), 1, "John Doe", 
                "LEAVE_APPROVED", "Your leave request has been approved", "EMAIL", "QUEUED", 
                "LEAVE_REQUEST", 101, 1, null, LocalDateTime.now(), null);

        return ResponseEntity.ok(item);
    }

    @PostMapping("/retry-failed")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Integer>> retryFailedNotifications(Authentication authentication) {

        // Simplified implementation - would retry all failed notifications
        Map<String, Integer> result = new HashMap<>();
        result.put("requeued", 3); // Number of notifications requeued

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{notificationId}/cancel")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<NotificationQueueItem> cancelNotification(
            @PathVariable Integer notificationId,
            Authentication authentication) {

        // Simplified implementation - would cancel actual notification
        NotificationQueueItem item = createNotificationQueueItem(notificationId.intValue(), 1, "John Doe", 
                "LEAVE_APPROVED", "Your leave request has been approved", "EMAIL", "CANCELLED", 
                "LEAVE_REQUEST", 101, 0, null, LocalDateTime.now(), null);

        return ResponseEntity.ok(item);
    }

    @GetMapping("/export")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<?> exportQueueLogs(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication) {

        // Simplified implementation - would generate actual export file
        // In real implementation, return CSV file stream
        return ResponseEntity.ok().build();
    }

    // Helper method
    private NotificationQueueItem createNotificationQueueItem(int id, int recipientId, String recipientName,
                                                            String templateCode, String subject, String channel,
                                                            String status, String relatedEntityType, int relatedEntityId,
                                                            int retryCount, LocalDateTime createdAt, LocalDateTime sentAt,
                                                            LocalDateTime scheduledAt) {
        NotificationQueueItem item = new NotificationQueueItem();
        item.setId(id);
        item.setRecipientId(recipientId);
        item.setRecipientName(recipientName);
        item.setTemplateCode(templateCode);
        item.setSubject(subject);
        item.setChannel(channel);
        item.setStatus(status);
        item.setRelatedEntityType(relatedEntityType);
        item.setRelatedEntityId(relatedEntityId);
        item.setRetryCount(retryCount);
        item.setScheduledAt(scheduledAt);
        item.setCreatedAt(createdAt);
        item.setSentAt(sentAt);
        return item;
    }
}