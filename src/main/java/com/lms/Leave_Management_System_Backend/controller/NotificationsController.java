package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.NotificationDto;
import com.lms.Leave_Management_System_Backend.dto.NotificationPreferences;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
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
@RequestMapping("/api/v1/notifications")
public class NotificationsController {

    @Autowired
    private NotificationQueueRepository notificationQueueRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponseWithUnreadCount> getNotifications(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        User currentUser = userRepository.findById(Long.parseLong(authentication.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        
        Page<NotificationQueue> notificationsPage;
        
        // Filter based on tab parameter
        if ("unread".equals(tab)) {
            notificationsPage = notificationQueueRepository.findByUserIdAndChannelAndStatus(
                currentUser.getId(), 
                NotificationQueue.Channel.IN_APP, 
                NotificationQueue.NotificationStatus.SENT, 
                pageable
            );
        } else {
            notificationsPage = notificationQueueRepository.findByUserIdAndChannel(
                currentUser.getId(), 
                NotificationQueue.Channel.IN_APP, 
                pageable
            );
        }
        
        List<NotificationDto> notifications = notificationsPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        // Count unread notifications (sent IN_APP notifications that haven't been read)
        List<NotificationQueue> unreadNotifications = notificationQueueRepository.findByUserIdAndStatus(
            currentUser.getId(), 
            NotificationQueue.NotificationStatus.SENT
        );
        long unreadCount = unreadNotifications.stream()
                .filter(n -> n.getIsRead() == null || !n.getIsRead())
                .filter(n -> n.getChannel() == NotificationQueue.Channel.IN_APP)
                .count();

        PageResponse pageResponse = new PageResponse(page, limit, notificationsPage.getTotalElements(), notificationsPage.getTotalPages());

        PaginatedResponseWithUnreadCount response = new PaginatedResponseWithUnreadCount();
        response.setSuccess(true);
        response.setData(notifications);
        response.setPage(pageResponse.getPage());
        response.setLimit(pageResponse.getLimit());
        response.setTotalCount((int) pageResponse.getTotalCount());
        response.setTotalPages(pageResponse.getTotalPages());
        response.setUnreadCount((int) unreadCount);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{notificationId}/read")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        User currentUser = userRepository.findById(Long.parseLong(authentication.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        // Ensure the notification belongs to the current user
        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        notification.setIsRead(true);
        notificationQueueRepository.save(notification);

        return ResponseEntity.ok(convertToDto(notification));
    }

    @PostMapping("/mark-all-read")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication authentication) {

        User currentUser = userRepository.findById(Long.parseLong(authentication.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<NotificationQueue> allUserNotifications = notificationQueueRepository.findByUserId(currentUser.getId());
        
        int updatedCount = 0;
        for (NotificationQueue notification : allUserNotifications) {
            if (notification.getIsRead() == null || !notification.getIsRead()) {
                notification.setIsRead(true);
                notificationQueueRepository.save(notification);
                updatedCount++;
            }
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("updated", updatedCount);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/preferences")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationPreferences> getNotificationPreferences(Authentication authentication) {

        User currentUser = userRepository.findById(Long.parseLong(authentication.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        // For now, return default preferences since we don't have a dedicated preferences table
        // In a real implementation, this would query a user_preferences table
        NotificationPreferences preferences = new NotificationPreferences();
        preferences.setLeaveRequestUpdates(true);
        preferences.setApprovalNotifications(true);
        preferences.setCompOffUpdates(true);
        preferences.setPolicyUpdates(false);
        preferences.setSystemNotifications(true);
        preferences.setHolidayReminders(true);

        return ResponseEntity.ok(preferences);
    }

    @PatchMapping("/preferences")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationPreferences> updateNotificationPreferences(
            @RequestBody NotificationPreferences preferences,
            Authentication authentication) {

        User currentUser = userRepository.findById(Long.parseLong(authentication.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        // For now, just return the preferences since we don't have a dedicated preferences table
        // In a real implementation, this would update a user_preferences table
        return ResponseEntity.ok(preferences);
    }

    // Helper method to convert NotificationQueue to NotificationDto
    private NotificationDto convertToDto(NotificationQueue notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId().intValue());
        dto.setCategory("REQUESTS"); // Could be derived from template code
        dto.setTitle(notification.getTemplateCode()); // Using template code as title for now
        dto.setDescription(notification.getPayload()); // Using payload as description for now
        dto.setIsRead(notification.getIsRead() != null ? notification.getIsRead() : false);
        dto.setRelatedEntityType(notification.getRelatedEntityType());
        dto.setRelatedEntityId(notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().intValue() : null);
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }

    // Custom response class for notifications with unread count
    public static class PaginatedResponseWithUnreadCount {
        private Boolean success;
        private List<NotificationDto> data;
        private Integer unreadCount;
        private int page;
        private int limit;
        private int totalCount;
        private int totalPages;

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public List<NotificationDto> getData() {
            return data;
        }

        public void setData(List<NotificationDto> data) {
            this.data = data;
        }

        public Integer getUnreadCount() {
            return unreadCount;
        }

        public void setUnreadCount(Integer unreadCount) {
            this.unreadCount = unreadCount;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
    }
}