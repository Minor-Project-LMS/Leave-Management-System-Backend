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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    @Transactional(readOnly = true)
    public ResponseEntity<PaginatedResponse<NotificationQueueItem>> getNotificationQueue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        
        Page<NotificationQueue> notificationQueuePage;
        
        // Build query based on filters using specification
        notificationQueuePage = notificationQueueRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            
            if (status != null && !status.isEmpty()) {
                try {
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.valueOf(status.toUpperCase())));
                } catch (IllegalArgumentException e) {
                    // Invalid status, ignore filter
                }
            }
            if (channel != null && !channel.isEmpty()) {
                try {
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.valueOf(channel.toUpperCase())));
                } catch (IllegalArgumentException e) {
                    // Invalid channel, ignore filter
                }
            }
            if (templateCode != null && !templateCode.isEmpty()) {
                predicates.add(cb.like(cb.upper(root.get("templateCode")), "%" + templateCode.toUpperCase() + "%"));
            }
            if (dateFrom != null && !dateFrom.isEmpty()) {
                try {
                    LocalDateTime fromDate = LocalDateTime.parse(dateFrom);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
                } catch (Exception e) {
                    // Invalid date format, ignore filter
                }
            }
            if (dateTo != null && !dateTo.isEmpty()) {
                try {
                    LocalDateTime toDate = LocalDateTime.parse(dateTo);
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
                } catch (Exception e) {
                    // Invalid date format, ignore filter
                }
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
        
        List<NotificationQueueItem> queueItems = notificationQueuePage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(page, limit, (int) notificationQueuePage.getTotalElements(), notificationQueuePage.getTotalPages());
        
        return ResponseEntity.ok(new PaginatedResponse<>(true, queueItems, pageResponse));
    }

    @PostMapping("/{notificationId}/retry")
    @RequireRole({"HR_ADMIN"})
    @Transactional
    public ResponseEntity<NotificationQueueItem> retryNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElse(null);
        
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if notification has reached the 3-retry cap
        if (notification.getRetryCount() != null && notification.getRetryCount() >= 3) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        
        notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
        notification.setSentAt(null);
        notification.setRetryCount((notification.getRetryCount() != null ? notification.getRetryCount() : 0) + 1);
        notificationQueueRepository.save(notification);

        return ResponseEntity.ok(convertToDto(notification));
    }

    @PostMapping("/retry-failed")
    @RequireRole({"HR_ADMIN"})
    @Transactional
    public ResponseEntity<Map<String, Integer>> retryFailedNotifications(Authentication authentication) {

        List<NotificationQueue> failedNotifications = notificationQueueRepository.findByStatus(NotificationQueue.NotificationStatus.FAILED);
        
        int requeuedCount = 0;
        for (NotificationQueue notification : failedNotifications) {
            // Only retry if under the 3-retry cap
            if (notification.getRetryCount() == null || notification.getRetryCount() < 3) {
                notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
                notification.setSentAt(null);
                notification.setRetryCount((notification.getRetryCount() != null ? notification.getRetryCount() : 0) + 1);
                notificationQueueRepository.save(notification);
                requeuedCount++;
            }
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("requeued", requeuedCount);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{notificationId}/cancel")
    @RequireRole({"HR_ADMIN"})
    @Transactional
    public ResponseEntity<NotificationQueueItem> cancelNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElse(null);
        
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if notification has already been sent
        if (notification.getStatus() == NotificationQueue.NotificationStatus.SENT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        
        notification.setStatus(NotificationQueue.NotificationStatus.CANCELLED);
        notificationQueueRepository.save(notification);

        return ResponseEntity.ok(convertToDto(notification));
    }

    @GetMapping("/export")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportQueueLogs(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication) {

        // Build query based on filters
        List<NotificationQueue> notifications = notificationQueueRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            
            if (dateFrom != null && !dateFrom.isEmpty()) {
                try {
                    LocalDateTime fromDate = LocalDateTime.parse(dateFrom);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
                } catch (Exception e) {
                    // Invalid date format, ignore filter
                }
            }
            if (dateTo != null && !dateTo.isEmpty()) {
                try {
                    LocalDateTime toDate = LocalDateTime.parse(dateTo);
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
                } catch (Exception e) {
                    // Invalid date format, ignore filter
                }
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, Sort.by("createdAt").descending());

        // Generate CSV content
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);
        
        // CSV Header
        writer.println("ID,Recipient ID,Recipient Name,Template Code,Channel,Status,Related Entity Type,Related Entity ID,Retry Count,Scheduled At,Created At,Sent At");
        
        // CSV Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (NotificationQueue notification : notifications) {
            String line = String.format("%d,%d,\"%s\",%s,%s,%s,%s,%s,%d,%s,%s,%s",
                notification.getId(),
                notification.getUser().getId(),
                escapeCsv(notification.getUser().getFullName()),
                notification.getTemplateCode(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getRelatedEntityType() != null ? notification.getRelatedEntityType() : "",
                notification.getRelatedEntityId() != null ? notification.getRelatedEntityId() : "",
                notification.getRetryCount() != null ? notification.getRetryCount() : 0,
                notification.getScheduledAt() != null ? notification.getScheduledAt().format(formatter) : "",
                notification.getCreatedAt() != null ? notification.getCreatedAt().format(formatter) : "",
                notification.getSentAt() != null ? notification.getSentAt().format(formatter) : ""
            );
            writer.println(line);
        }
        
        writer.flush();
        writer.close();
        
        byte[] csvBytes = outputStream.toByteArray();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "notification_queue_export.csv");
        headers.setContentLength(csvBytes.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
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
        item.setSourceEventId(notification.getSourceEventId() != null ? notification.getSourceEventId().intValue() : null);
        item.setKafkaTopic(notification.getKafkaTopic());
        item.setKafkaPartition(notification.getKafkaPartition());
        item.setKafkaOffset(notification.getKafkaOffset());
        return item;
    }

    // Helper method to escape CSV values
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}