package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.model.UserNotificationPreference;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserNotificationPreferenceRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationsControllerTest {

    private NotificationQueueRepository queue;
    private UserRepository users;
    private UserNotificationPreferenceRepository prefs;
    private MockMvc mvc;
    private User me;

    @BeforeEach
    void setUp() {
        queue = mock(NotificationQueueRepository.class);
        users = mock(UserRepository.class);
        prefs = mock(UserNotificationPreferenceRepository.class);
        NotificationsController controller = new NotificationsController();
        ReflectionTestUtils.setField(controller, "notificationQueueRepository", queue);
        ReflectionTestUtils.setField(controller, "userRepository", users);
        ReflectionTestUtils.setField(controller, "userNotificationPreferenceRepository", prefs);
        mvc = mockMvc(controller);

        me = employee(1L, "emp@lms.com");
        when(users.findByEmailIgnoreCase("emp@lms.com")).thenReturn(Optional.of(me));
        when(prefs.saveAndFlush(any(UserNotificationPreference.class))).thenAnswer(i -> i.getArgument(0));
    }

    private NotificationQueue inApp(Long id, String entityType, String payload) {
        NotificationQueue n = notification(id, me, NotificationQueue.Channel.IN_APP, "LEAVE_APPROVED", payload);
        n.setRelatedEntityType(entityType);
        n.setRelatedEntityId(100L);
        return n;
    }

    @Test
    @DisplayName("'all' tab parses JSON payloads and categorises by entity type")
    void allTab() throws Exception {
        when(queue.findByUserIdAndChannel(eq(1L), eq(NotificationQueue.Channel.IN_APP), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        inApp(1L, "LEAVE_REQUEST", "{\"title\":\"Submitted\",\"message\":\"Your leave was submitted\"}"),
                        inApp(2L, "LEAVE_APPROVAL", "{\"title\":\"Approved\",\"description\":\"desc text\"}"),
                        inApp(3L, "SYSTEM", "plain text"))));
        when(queue.countByUserIdAndChannelAndIsReadFalse(1L, NotificationQueue.Channel.IN_APP)).thenReturn(2L);

        mvc.perform(get("/api/v1/notifications").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.data[0].title").value("Submitted"))
                .andExpect(jsonPath("$.data[0].description").value("Your leave was submitted"))
                .andExpect(jsonPath("$.data[0].category").value("REQUESTS"))
                .andExpect(jsonPath("$.data[1].description").value("desc text"))
                .andExpect(jsonPath("$.data[1].category").value("APPROVALS"))
                .andExpect(jsonPath("$.data[2].title").value("LEAVE_APPROVED"))
                .andExpect(jsonPath("$.data[2].description").value("plain text"))
                .andExpect(jsonPath("$.data[2].category").value("SYSTEM"));
    }

    @Test
    void tabsQueryTheRightEntityTypes() throws Exception {
        when(queue.findByUserIdAndChannelAndIsReadFalse(anyLong(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(queue.findByUserIdAndChannelAndRelatedEntityTypeIn(anyLong(), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        for (String tab : List.of("unread", "requests", "approvals", "system")) {
            mvc.perform(get("/api/v1/notifications").param("tab", tab).principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk());
        }

        verify(queue).findByUserIdAndChannelAndIsReadFalse(eq(1L), eq(NotificationQueue.Channel.IN_APP), any(Pageable.class));
        verify(queue).findByUserIdAndChannelAndRelatedEntityTypeIn(eq(1L), eq(NotificationQueue.Channel.IN_APP),
                eq(List.of("LEAVE_REQUEST", "COMP_OFF_REQUEST")), any(Pageable.class));
        verify(queue).findByUserIdAndChannelAndRelatedEntityTypeIn(eq(1L), eq(NotificationQueue.Channel.IN_APP),
                eq(List.of("LEAVE_APPROVAL", "COMP_OFF_APPROVAL")), any(Pageable.class));
        verify(queue).findByUserIdAndChannelAndRelatedEntityTypeIn(eq(1L), eq(NotificationQueue.Channel.IN_APP),
                eq(List.of("SYSTEM", "POLICY", "HOLIDAY", "LEAVE_SUBMITTED")), any(Pageable.class));
    }

    @Test
    void markAsRead() throws Exception {
        NotificationQueue n = inApp(5L, "SYSTEM", "{}");
        when(queue.findById(5L)).thenReturn(Optional.of(n));
        when(queue.saveAndFlush(n)).thenReturn(n);

        mvc.perform(post("/api/v1/notifications/5/read").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
        assertThat(n.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("users cannot mark someone else's notification as read")
    void cannotMarkOthersNotification() throws Exception {
        NotificationQueue n = notification(6L, employee(9L, "x@lms.com"), NotificationQueue.Channel.IN_APP, "X", "{}");
        when(queue.findById(6L)).thenReturn(Optional.of(n));

        mvc.perform(post("/api/v1/notifications/6/read").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isInternalServerError());
        verify(queue, never()).saveAndFlush(any());
    }

    @Test
    void markAllAsRead() throws Exception {
        NotificationQueue a = inApp(1L, "SYSTEM", "{}");
        NotificationQueue b = inApp(2L, "SYSTEM", "{}");
        when(queue.findByUserIdAndIsReadFalse(1L)).thenReturn(List.of(a, b));

        mvc.perform(post("/api/v1/notifications/mark-all-read").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.unreadCount").value(0));
        assertThat(a.getIsRead()).isTrue();
        assertThat(b.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("preferences are created with defaults on first read")
    void preferencesDefaults() throws Exception {
        when(prefs.findByUserId(1L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/notifications/preferences").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveRequestUpdates").value(true))
                .andExpect(jsonPath("$.policyUpdates").value(false));
        verify(prefs).saveAndFlush(any(UserNotificationPreference.class));
    }

    @Test
    void updatePreferencesOnlyChangesProvidedFlags() throws Exception {
        UserNotificationPreference existing = new UserNotificationPreference();
        existing.setUser(me);
        when(prefs.findByUserId(1L)).thenReturn(Optional.of(existing));

        mvc.perform(patch("/api/v1/notifications/preferences").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("policyUpdates", true, "systemNotifications", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyUpdates").value(true))
                .andExpect(jsonPath("$.systemNotifications").value(false))
                .andExpect(jsonPath("$.compOffUpdates").value(true));
    }

    @Test
    void unknownUserIs500() throws Exception {
        mvc.perform(get("/api/v1/notifications").principal(auth("ghost@lms.com", "EMPLOYEE")))
                .andExpect(status().isInternalServerError());
    }
}
