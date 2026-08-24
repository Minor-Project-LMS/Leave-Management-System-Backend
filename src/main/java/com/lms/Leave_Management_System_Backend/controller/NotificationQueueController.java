package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.NotificationQueueItem;
import com.lms.Leave_Management_System_Backend.dto.PaginatedResponse;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notification-queue")
public class NotificationQueueController {

    @Autowired
    private NotificationQueueRepository notificationQueueRepository;

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

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<NotificationQueue> notificationQueuePage;
        
        // Build query based on filters using specification
        if (status != null || channel != null || templateCode != null || dateFrom != null || dateTo != null) {
            notificationQueuePage = notificationQueueRepository.findAll((root, query, cb) -> {
                var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                
                if (status != null) {
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.valueOf(status)));
                }
                if (channel != null) {
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.valueOf(channel)));
                }
                if (templateCode != null) {
                    predicates.add(cb.equal(root.get("templateCode"), templateCode));
                }
                if (dateFrom != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDateTime.parse(dateFrom)));
                }
                if (dateTo != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), LocalDateTime.parse(dateTo)));
                }
                
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }, pageable);
        } else {
            notificationQueuePage = notificationQueueRepository.findAll(pageable);
        }
        
        List<NotificationQueueItem> queueItems = notificationQueuePage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(page, size, (int) notificationQueuePage.getTotalElements(), notificationQueuePage.getTotalPages());
        
        return ResponseEntity.ok(new PaginatedResponse<>(true, queueItems, pageResponse));
    }

    @PostMapping("/{notificationId}/retry")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<NotificationQueueItem> retryNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
        notification.setSentAt(null);
        notificationQueueRepository.save(notification);

        return ResponseEntity.ok(convertToDto(notification));
    }

    @PostMapping("/retry-failed")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Integer>> retryFailedNotifications(Authentication authentication) {

        List<NotificationQueue> failedNotifications = notificationQueueRepository.findByStatus(NotificationQueue.NotificationStatus.FAILED);
        
        int requeuedCount = 0;
        for (NotificationQueue notification : failedNotifications) {
            notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
            notification.setSentAt(null);
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationQueueRepository.save(notification);
            requeuedCount++;
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("requeued", requeuedCount);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{notificationId}/cancel")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<NotificationQueueItem> cancelNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        notification.setStatus(NotificationQueue.NotificationStatus.CANCELLED);
        notificationQueueRepository.save(notification);

        return ResponseEntity.ok(convertToDto(notification));
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

    // Helper method to convert entity to DTO
    private NotificationQueueItem convertToDto(NotificationQueue notification) {
        NotificationQueueItem item = new NotificationQueueItem();
        item.setId(notification.getId().intValue());
        item.setRecipientId(notification.getUser().getId().intValue());
        item.setRecipientName(notification.getUser().getName());
        item.setTemplateCode(notification.getTemplateCode());
        item.setSubject(notification.getTemplateCode()); // Using template code as subject for now
        item.setChannel(notification.getChannel().name());
        item.setStatus(notification.getStatus().name());
        item.setRelatedEntityType(notification.getRelatedEntityType());
        item.setRelatedEntityId(notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().intValue() : null);
        item.setRetryCount(notification.getRetryCount() != null ? notification.getRetryCount() : 0);
        item.setScheduledAt(notification.getScheduledAt());
        item.setCreatedAt(notification.getCreatedAt());
        item.setSentAt(notification.getSentAt());
        return item;
    }
}