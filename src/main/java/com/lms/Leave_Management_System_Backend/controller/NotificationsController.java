package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.NotificationDto;
import com.lms.Leave_Management_System_Backend.dto.NotificationPreferences;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.model.UserNotificationPreferences;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserNotificationPreferencesRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
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

    @Autowired
    private UserNotificationPreferencesRepository userNotificationPreferencesRepository;

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<PaginatedResponseWithUnreadCount> getNotifications(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        
        Page<NotificationQueue> notificationsPage;
        
        // Filter based on tab parameter
        switch (tab.toLowerCase()) {
            case "unread":
                notificationsPage = notificationQueueRepository.findAll((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.IN_APP));
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.SENT));
                    predicates.add(cb.or(
                        cb.isNull(root.get("isRead")),
                        cb.equal(root.get("isRead"), false)
                    ));
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, pageable);
                break;
            case "requests":
                notificationsPage = notificationQueueRepository.findAll((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.IN_APP));
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.SENT));
                    // Filter for request-related template codes
                    predicates.add(cb.or(
                        cb.like(cb.upper(root.get("templateCode")), "%LEAVE%"),
                        cb.like(cb.upper(root.get("templateCode")), "%REQUEST%"),
                        cb.like(cb.upper(root.get("templateCode")), "%WITHDRAW%"),
                        cb.like(cb.upper(root.get("templateCode")), "%COMP%")
                    ));
                    // Exclude approval-related codes
                    predicates.add(cb.not(cb.or(
                        cb.like(cb.upper(root.get("templateCode")), "%APPROVAL%"),
                        cb.like(cb.upper(root.get("templateCode")), "%APPROVED%"),
                        cb.like(cb.upper(root.get("templateCode")), "%REJECTED%")
                    )));
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, pageable);
                break;
            case "approvals":
                notificationsPage = notificationQueueRepository.findAll((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.IN_APP));
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.SENT));
                    // Filter for approval-related template codes
                    predicates.add(cb.or(
                        cb.like(cb.upper(root.get("templateCode")), "%APPROVAL%"),
                        cb.like(cb.upper(root.get("templateCode")), "%APPROVED%"),
                        cb.like(cb.upper(root.get("templateCode")), "%REJECTED%")
                    ));
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, pageable);
                break;
            case "system":
                notificationsPage = notificationQueueRepository.findAll((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.IN_APP));
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.SENT));
                    // Filter for system-related template codes
                    predicates.add(cb.or(
                        cb.like(cb.upper(root.get("templateCode")), "%SYSTEM%"),
                        cb.like(cb.upper(root.get("templateCode")), "%ACCOUNT%"),
                        cb.like(cb.upper(root.get("templateCode")), "%PASSWORD%")
                    ));
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, pageable);
                break;
            case "policy":
                notificationsPage = notificationQueueRepository.findAll((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.IN_APP));
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.SENT));
                    // Filter for policy-related template codes
                    predicates.add(cb.or(
                        cb.like(cb.upper(root.get("templateCode")), "%POLICY%"),
                        cb.like(cb.upper(root.get("templateCode")), "%RULE%")
                    ));
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, pageable);
                break;
            case "all":
            default:
                notificationsPage = notificationQueueRepository.findAll((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
                    predicates.add(cb.equal(root.get("channel"), NotificationQueue.Channel.IN_APP));
                    predicates.add(cb.equal(root.get("status"), NotificationQueue.NotificationStatus.SENT));
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, pageable);
                break;
        }
        
        List<NotificationDto> notifications = notificationsPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        // Count unread notifications (sent IN_APP notifications that haven't been read)
        List<NotificationQueue> allUnread = notificationQueueRepository.findByUserIdAndStatus(
            currentUser.getId(), 
            NotificationQueue.NotificationStatus.SENT
        );
        long unreadCount = allUnread.stream()
                .filter(n -> n.getChannel() == NotificationQueue.Channel.IN_APP)
                .filter(n -> n.getIsRead() == null || !n.getIsRead())
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
    @Transactional
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElse(null);
        
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Ensure the notification belongs to the current user
        if (!notification.getUser().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        notification.setIsRead(true);
        notification.setReadAt(java.time.LocalDateTime.now());
        notificationQueueRepository.save(notification);

        return ResponseEntity.ok(convertToDto(notification));
    }

    @PostMapping("/mark-all-read")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<NotificationQueue> allUserNotifications = notificationQueueRepository.findByUserId(currentUser.getId());
        
        int updatedCount = 0;
        for (NotificationQueue notification : allUserNotifications) {
            // Only mark IN_APP notifications that are SENT and unread
            if (notification.getChannel() == NotificationQueue.Channel.IN_APP 
                && notification.getStatus() == NotificationQueue.NotificationStatus.SENT
                && (notification.getIsRead() == null || !notification.getIsRead())) {
                notification.setIsRead(true);
                notification.setReadAt(java.time.LocalDateTime.now());
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
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationPreferences> getNotificationPreferences(Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

//        UserNotificationPreferences preferencesEntity = userNotificationPreferencesRepository
//                .findByUserId(currentUser.getId())
//                .orElseGet(() -> {
//                    // Create default preferences if not exist
//                    UserNotificationPreferences newPreferences = new UserNotificationPreferences(currentUser);
//                    return userNotificationPreferencesRepository.save(newPreferences);
//                });
        UserNotificationPreferences preferencesEntity = userNotificationPreferencesRepository
                .findByUserId(currentUser.getId())
                .orElseGet(() -> new UserNotificationPreferences(currentUser));

        return ResponseEntity.ok(convertToDto(preferencesEntity));
    }

    @PatchMapping("/preferences")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<NotificationPreferences> updateNotificationPreferences(
            @RequestBody NotificationPreferences preferences,
            Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserNotificationPreferences preferencesEntity = userNotificationPreferencesRepository
                .findByUserId(currentUser.getId())
                .orElseGet(() -> new UserNotificationPreferences(currentUser));

        // Update fields from request
        if (preferences.getLeaveRequestUpdates() != null) {
            preferencesEntity.setLeaveRequestUpdates(preferences.getLeaveRequestUpdates());
        }
        if (preferences.getApprovalNotifications() != null) {
            preferencesEntity.setApprovalNotifications(preferences.getApprovalNotifications());
        }
        if (preferences.getCompOffUpdates() != null) {
            preferencesEntity.setCompOffUpdates(preferences.getCompOffUpdates());
        }
        if (preferences.getPolicyUpdates() != null) {
            preferencesEntity.setPolicyUpdates(preferences.getPolicyUpdates());
        }
        if (preferences.getSystemNotifications() != null) {
            preferencesEntity.setSystemNotifications(preferences.getSystemNotifications());
        }
        if (preferences.getHolidayReminders() != null) {
            preferencesEntity.setHolidayReminders(preferences.getHolidayReminders());
        }

        UserNotificationPreferences savedPreferences = userNotificationPreferencesRepository.save(preferencesEntity);

        return ResponseEntity.ok(convertToDto(savedPreferences));
    }

    // Helper method to convert NotificationQueue to NotificationDto
    private NotificationDto convertToDto(NotificationQueue notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId().intValue());
        dto.setCategory(deriveCategoryFromTemplateCode(notification.getTemplateCode()));
        dto.setTitle(notification.getTemplateCode()); // Using template code as title for now
        dto.setDescription(notification.getPayload()); // Using payload as description for now
        dto.setIsRead(notification.getIsRead() != null ? notification.getIsRead() : false);
        dto.setRelatedEntityType(notification.getRelatedEntityType());
        dto.setRelatedEntityId(notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().intValue() : null);
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }

    // Helper method to derive category from template code
    private String deriveCategoryFromTemplateCode(String templateCode) {
        if (templateCode == null) {
            return "SYSTEM";
        }
        
        String upperCode = templateCode.toUpperCase();
        
        if (upperCode.contains("APPROVAL") || upperCode.contains("APPROVED") || upperCode.contains("REJECTED")) {
            return "APPROVALS";
        } else if (upperCode.contains("LEAVE") || upperCode.contains("REQUEST") || upperCode.contains("WITHDRAW")) {
            return "REQUESTS";
        } else if (upperCode.contains("POLICY") || upperCode.contains("RULE") || upperCode.contains("UPDATE")) {
            return "POLICY";
        } else {
            return "SYSTEM";
        }
    }

    // Helper method to convert UserNotificationPreferences to NotificationPreferences DTO
    private NotificationPreferences convertToDto(UserNotificationPreferences entity) {
        NotificationPreferences dto = new NotificationPreferences();
        dto.setLeaveRequestUpdates(entity.getLeaveRequestUpdates());
        dto.setApprovalNotifications(entity.getApprovalNotifications());
        dto.setCompOffUpdates(entity.getCompOffUpdates());
        dto.setPolicyUpdates(entity.getPolicyUpdates());
        dto.setSystemNotifications(entity.getSystemNotifications());
        dto.setHolidayReminders(entity.getHolidayReminders());
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