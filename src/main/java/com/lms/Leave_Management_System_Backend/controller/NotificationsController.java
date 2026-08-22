package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.NotificationDto;
import com.lms.Leave_Management_System_Backend.dto.NotificationPreferences;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationsController {

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponseWithUnreadCount> getNotifications(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Simplified implementation - would query actual notifications
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        // Mock data for demonstration
        List<NotificationDto> notifications = List.of(
                createNotification(1, "REQUESTS", "Leave Request Submitted", 
                        "Your leave request has been submitted successfully", false, 
                        "LEAVE_REQUEST", 101, LocalDateTime.now()),
                createNotification(2, "APPROVALS", "Leave Request Approved", 
                        "Your leave request has been approved", true, 
                        "LEAVE_REQUEST", 101, LocalDateTime.now().minusDays(1)),
                createNotification(3, "SYSTEM", "System Maintenance", 
                        "System maintenance scheduled for this weekend", false, 
                        null, null, LocalDateTime.now().minusDays(2))
        );

        PageResponse pageResponse = new PageResponse(page, size, notifications.size(), 1);
        
        PaginatedResponseWithUnreadCount response = new PaginatedResponseWithUnreadCount();
        response.setSuccess(true);
        response.setData(notifications);
        response.setPage(pageResponse.getPage());
        response.setLimit(pageResponse.getSize());
        response.setTotalCount((int) pageResponse.getTotalElements());
        response.setTotalPages(pageResponse.getTotalPages());
        response.setUnreadCount(2); // Count of unread notifications

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{notificationId}/read")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Integer notificationId,
            Authentication authentication) {

        // Simplified implementation - would update actual notification
        NotificationDto notification = createNotification(notificationId, "REQUESTS", 
                "Leave Request Submitted", "Your leave request has been submitted successfully", 
                true, "LEAVE_REQUEST", 101, LocalDateTime.now());

        return ResponseEntity.ok(notification);
    }

    @PostMapping("/mark-all-read")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication authentication) {

        // Simplified implementation - would update all notifications
        Map<String, Integer> result = new HashMap<>();
        result.put("updated", 5); // Number of notifications marked as read

        return ResponseEntity.ok(result);
    }

    @GetMapping("/preferences")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationPreferences> getNotificationPreferences(Authentication authentication) {

        // Simplified implementation - would query actual user preferences
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

        // Simplified implementation - would update actual user preferences
        return ResponseEntity.ok(preferences);
    }

    // Helper method
    private NotificationDto createNotification(Integer id, String category, String title, 
                                               String description, boolean isRead, 
                                               String relatedEntityType, Integer relatedEntityId, 
                                               LocalDateTime createdAt) {
        NotificationDto notification = new NotificationDto();
        notification.setId(id);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setDescription(description);
        notification.setIsRead(isRead);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setRelatedEntityId(relatedEntityId);
        notification.setCreatedAt(createdAt);
        return notification;
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