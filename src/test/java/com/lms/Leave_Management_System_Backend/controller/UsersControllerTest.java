package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.AttachmentDto;
import com.lms.Leave_Management_System_Backend.dto.AttachmentInitUploadResponse;
import com.lms.Leave_Management_System_Backend.dto.UserDto;
import com.lms.Leave_Management_System_Backend.model.Attachment;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.model.UserNotificationPreference;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserNotificationPreferenceRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import com.lms.Leave_Management_System_Backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

class UsersControllerTest {

    private UserRepository users;
    private AuthService authService;
    private PasswordEncoder encoder;
    private AttachmentService attachments;
    private NotificationQueueRepository notifications;
    private UserNotificationPreferenceRepository prefs;
    private MockMvc mvc;
    private User me;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        authService = mock(AuthService.class);
        encoder = new BCryptPasswordEncoder();
        attachments = mock(AttachmentService.class);
        notifications = mock(NotificationQueueRepository.class);
        prefs = mock(UserNotificationPreferenceRepository.class);
        mvc = mockMvc(new UsersController(users, authService, encoder, attachments, notifications, prefs));

        me = employee(1L, "emp@lms.com");
        me.setName("Asha");
        me.setPasswordHash(encoder.encode("Old@1234"));
        when(users.findByEmailIgnoreCase("emp@lms.com")).thenReturn(Optional.of(me));
        when(authService.getUserByEmail("emp@lms.com")).thenReturn(new UserDto(1L, "Asha", "emp@lms.com", "EMPLOYEE"));
    }

    @Test
    @DisplayName("directory excludes the caller, filters by name and sorts alphabetically")
    void directory() throws Exception {
        User zara = user(5L, "Zara Khan", "z@lms.com", "EMPLOYEE");
        User arjun = user(6L, "Arjun Rao", "a@lms.com", "EMPLOYEE");
        arjun.setDepartment(department(10, "Engineering"));
        User ravi = user(7L, "Ravi", "r@lms.com", "EMPLOYEE");
        when(users.findByEmploymentStatus(User.EmploymentStatus.ACTIVE)).thenReturn(List.of(me, zara, arjun, ravi));

        mvc.perform(get("/api/v1/users/directory").param("q", "A").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fullName").value("Arjun Rao"))
                .andExpect(jsonPath("$[0].departmentName").value("Engineering"))
                .andExpect(jsonPath("$[1].fullName").value("Ravi"))
                .andExpect(jsonPath("$[2].fullName").value("Zara Khan"));
    }

    @Test
    void myProfile() throws Exception {
        mvc.perform(get("/api/v1/users/me").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("emp@lms.com"));
    }

    @Test
    void updateProfileNotifiesWhenSomethingChanged() throws Exception {
        mvc.perform(patch("/api/v1/users/me").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Asha R", "phoneNumber", "9999999999"))))
                .andExpect(status().isOk());

        assertThat(me.getName()).isEqualTo("Asha R");
        assertThat(me.getPhone()).isEqualTo("9999999999");
        ArgumentCaptor<NotificationQueue> n = ArgumentCaptor.forClass(NotificationQueue.class);
        verify(notifications, times(2)).save(n.capture()); // IN_APP + EMAIL
        assertThat(n.getAllValues()).extracting(NotificationQueue::getTemplateCode).containsOnly("PROFILE_UPDATED");
    }

    @Test
    void updateProfileWithNoChangesSendsNothing() throws Exception {
        mvc.perform(patch("/api/v1/users/me").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Asha"))))
                .andExpect(status().isOk());
        verify(notifications, never()).save(any());
    }

    @Test
    @DisplayName("system notifications preference OFF suppresses the notification")
    void preferenceOffSuppressesNotification() throws Exception {
        UserNotificationPreference pref = new UserNotificationPreference();
        pref.setSystemNotifications(false);
        when(prefs.findByUserId(1L)).thenReturn(Optional.of(pref));

        mvc.perform(patch("/api/v1/users/me").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "New Name"))))
                .andExpect(status().isOk());
        verify(notifications, never()).save(any());
    }

    @Test
    void changePasswordSuccess() throws Exception {
        mvc.perform(post("/api/v1/users/me/password").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Old@1234", "newPassword", "New@5678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(encoder.matches("New@5678", me.getPasswordHash())).isTrue();
        verify(users).save(me);
    }

    @Test
    void changePasswordWrongCurrentIs401() throws Exception {
        mvc.perform(post("/api/v1/users/me/password").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "nope", "newPassword", "New@5678"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD"));
        verify(users, never()).save(any());
    }

    @Test
    void myAvatar() throws Exception {
        when(attachments.resolveAvatarUrl(1L)).thenReturn("https://cdn/me.png");
        mvc.perform(get("/api/v1/users/me/avatar").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn/me.png"));
    }

    @Test
    void managerCanSeeOnlyDirectReportsAvatar() throws Exception {
        User mgr = manager(2L, "mgr@lms.com");
        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(mgr));
        User report = employee(5L, "r@lms.com");
        report.setReportsTo(mgr);
        User stranger = employee(6L, "s@lms.com");
        when(users.findById(5L)).thenReturn(Optional.of(report));
        when(users.findById(6L)).thenReturn(Optional.of(stranger));
        when(attachments.resolveAvatarUrl(5L)).thenReturn("/r.png");

        mvc.perform(get("/api/v1/users/5/avatar").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("/r.png"));
        mvc.perform(get("/api/v1/users/6/avatar").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initAvatarUpload() throws Exception {
        when(attachments.initializeUpload(eq(Attachment.EntityType.USER_AVATAR), eq(1L), any(), eq(1L)))
                .thenReturn(new AttachmentInitUploadResponse(42L, "https://s3/put", "user_avatar/1/x.png"));

        mvc.perform(post("/api/v1/users/me/avatar/init-upload").principal(auth("emp@lms.com", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fileName", "me.png", "contentType", "image/png", "sizeBytes", 1000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attachmentId").value(42))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3/put"));
    }

    @Test
    void confirmAvatarUpload() throws Exception {
        AttachmentDto withUrl = new AttachmentDto();
        withUrl.setDownloadUrl("https://s3/get");
        when(attachments.confirmUpload(eq(42L), any())).thenReturn(new AttachmentDto());
        when(attachments.getAttachment(42L)).thenReturn(withUrl);
        when(attachments.resolveAvatarUrl(1L)).thenReturn("https://s3/get");

        mvc.perform(post("/api/v1/users/me/avatar/42/confirm").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://s3/get"));

        assertThat(me.getAvatarUrl()).isEqualTo("https://s3/get");
        verify(users).save(me);
    }
}
