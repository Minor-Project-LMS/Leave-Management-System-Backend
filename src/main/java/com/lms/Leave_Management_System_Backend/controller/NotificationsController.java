package com.lms.Leave_Management_System_Backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.Leave_Management_System_Backend.dto.NotificationDto;
import com.lms.Leave_Management_System_Backend.dto.NotificationPreferences;
import com.lms.Leave_Management_System_Backend.dto.PageResponse;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.model.UserNotificationPreference;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.repository.UserNotificationPreferenceRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationsController {

    @Autowired
    private NotificationQueueRepository notificationQueueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserNotificationPreferenceRepository preferenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponseWithUnreadCount> getNotifications(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());
        Page<NotificationQueue> notificationsPage;

        switch (tab.toLowerCase()) {
            case "unread":
                notificationsPage = notificationQueueRepository.findByUserIdAndChannelAndIsReadFalse(
                        currentUser.getId(), NotificationQueue.Channel.IN_APP, pageable);
                break;
            case "requests":
                notificationsPage = notificationQueueRepository.findByUserIdAndChannelAndRelatedEntityTypeIn(
                        currentUser.getId(), NotificationQueue.Channel.IN_APP, Arrays.asList("LEAVE_REQUEST", "COMP_OFF_REQUEST"), pageable);
                break;
            case "approvals":
                notificationsPage = notificationQueueRepository.findByUserIdAndChannelAndRelatedEntityTypeIn(
                        currentUser.getId(), NotificationQueue.Channel.IN_APP, Arrays.asList("LEAVE_APPROVAL", "COMP_OFF_APPROVAL"), pageable);
                break;
            case "system":
                notificationsPage = notificationQueueRepository.findByUserIdAndChannelAndRelatedEntityTypeIn(
                        currentUser.getId(), NotificationQueue.Channel.IN_APP, Arrays.asList("SYSTEM", "POLICY", "HOLIDAY", "LEAVE_SUBMITTED"), pageable);
                break;
            default:
                notificationsPage = notificationQueueRepository.findByUserIdAndChannel(
                        currentUser.getId(), NotificationQueue.Channel.IN_APP, pageable);
                break;
        }

        List<NotificationDto> notifications = notificationsPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        // Count actual unread messages specifically for the logged in user
        long unreadCount = notificationQueueRepository.countByUserIdAndChannelAndIsReadFalse(
                currentUser.getId(), NotificationQueue.Channel.IN_APP);

        PaginatedResponseWithUnreadCount response = new PaginatedResponseWithUnreadCount();
        response.setSuccess(true);
        response.setData(notifications);
        response.setPage(page);
        response.setLimit(limit);
        response.setTotalCount((int) notificationsPage.getTotalElements());
        response.setTotalPages(notificationsPage.getTotalPages());
        response.setUnreadCount((int) unreadCount);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{notificationId}/read")
    @Transactional
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        NotificationQueue notification = notificationQueueRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Unauthorized notification modification");
        }

        notification.setIsRead(true);
        NotificationQueue updated = notificationQueueRepository.saveAndFlush(notification);

        return ResponseEntity.ok(convertToDto(updated));
    }

    @PostMapping("/mark-all-read")
    @Transactional
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<NotificationQueue> unreadList = notificationQueueRepository.findByUserIdAndIsReadFalse(currentUser.getId());

        for (NotificationQueue notification : unreadList) {
            notification.setIsRead(true);
        }
        notificationQueueRepository.saveAllAndFlush(unreadList);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("updatedCount", unreadList.size());
        result.put("unreadCount", 0);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/preferences")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationPreferences> getNotificationPreferences(Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserNotificationPreference dbPref = preferenceRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> createDefaultPreferences(currentUser));

        return ResponseEntity.ok(mapToPreferencesDto(dbPref));
    }

    @PatchMapping("/preferences")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<NotificationPreferences> updateNotificationPreferences(
            @RequestBody NotificationPreferences dto,
            Authentication authentication) {

        User currentUser = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserNotificationPreference dbPref = preferenceRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> {
                    UserNotificationPreference pref = new UserNotificationPreference();
                    pref.setUser(currentUser);
                    return pref;
                });

        if (dto.getLeaveRequestUpdates() != null) dbPref.setLeaveRequestUpdates(dto.getLeaveRequestUpdates());
        if (dto.getApprovalNotifications() != null) dbPref.setApprovalNotifications(dto.getApprovalNotifications());
        if (dto.getCompOffUpdates() != null) dbPref.setCompOffUpdates(dto.getCompOffUpdates());
        if (dto.getPolicyUpdates() != null) dbPref.setPolicyUpdates(dto.getPolicyUpdates());
        if (dto.getSystemNotifications() != null) dbPref.setSystemNotifications(dto.getSystemNotifications());
        if (dto.getHolidayReminders() != null) dbPref.setHolidayReminders(dto.getHolidayReminders());
        dbPref.setUpdatedAt(LocalDateTime.now());

        UserNotificationPreference saved = preferenceRepository.save(dbPref);
        return ResponseEntity.ok(mapToPreferencesDto(saved));
    }

    private UserNotificationPreference createDefaultPreferences(User user) {
        UserNotificationPreference pref = new UserNotificationPreference();
        pref.setUser(user);
        return preferenceRepository.save(pref);
    }

    private NotificationPreferences mapToPreferencesDto(UserNotificationPreference dbPref) {
        NotificationPreferences dto = new NotificationPreferences();
        dto.setLeaveRequestUpdates(dbPref.getLeaveRequestUpdates());
        dto.setApprovalNotifications(dbPref.getApprovalNotifications());
        dto.setCompOffUpdates(dbPref.getCompOffUpdates());
        dto.setPolicyUpdates(dbPref.getPolicyUpdates());
        dto.setSystemNotifications(dbPref.getSystemNotifications());
        dto.setHolidayReminders(dbPref.getHolidayReminders());
        return dto;
    }

    private NotificationDto convertToDto(NotificationQueue notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId().intValue());
        dto.setIsRead(Boolean.TRUE.equals(notification.getIsRead()));
        dto.setRelatedEntityType(notification.getRelatedEntityType());
        dto.setRelatedEntityId(notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().intValue() : null);
        dto.setCreatedAt(notification.getCreatedAt());

        String title = notification.getTemplateCode() != null ? notification.getTemplateCode() : "Notification";
        String description = notification.getPayload() != null ? notification.getPayload() : "";

        if (description.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(description);
                if (node.has("title")) title = node.get("title").asText();
                if (node.has("message")) description = node.get("message").asText();
                else if (node.has("description")) description = node.get("description").asText();
            } catch (Exception ignored) {}
        }

        dto.setTitle(title);
        dto.setDescription(description);

        String entityType = notification.getRelatedEntityType() != null ? notification.getRelatedEntityType() : "";
        if (entityType.contains("REQUEST")) {
            dto.setCategory("REQUESTS");
        } else if (entityType.contains("APPROVAL")) {
            dto.setCategory("APPROVALS");
        } else {
            dto.setCategory("SYSTEM");
        }

        return dto;
    }

    public static class PaginatedResponseWithUnreadCount {
        private Boolean success;
        private List<NotificationDto> data;
        private Integer unreadCount;
        private int page;
        private int limit;
        private int totalCount;
        private int totalPages;

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public List<NotificationDto> getData() { return data; }
        public void setData(List<NotificationDto> data) { this.data = data; }
        public Integer getUnreadCount() { return unreadCount; }
        public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }
}