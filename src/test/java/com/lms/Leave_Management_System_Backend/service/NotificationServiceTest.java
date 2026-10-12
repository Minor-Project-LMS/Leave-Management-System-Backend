package com.lms.Leave_Management_System_Backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationQueueRepository queueRepository;
    private UserRepository userRepository;
    private EmailService emailService;
    private NotificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        queueRepository = mock(NotificationQueueRepository.class);
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        service = new NotificationService(queueRepository, userRepository, emailService, new ObjectMapper());
        user = employee(1L, "asha@lms.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private void queue(NotificationQueue... items) {
        when(queueRepository.findByStatusAndScheduledAtBefore(eq(NotificationQueue.NotificationStatus.QUEUED),
                any(LocalDateTime.class))).thenReturn(List.of(items));
    }

    @Test
    @DisplayName("IN_APP notifications are simply marked SENT")
    void inAppMarkedSent() {
        NotificationQueue n = notification(1L, user, NotificationQueue.Channel.IN_APP, "LEAVE_APPROVED", "{}");
        queue(n);

        service.processQueuedNotifications();

        assertThat(n.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.SENT);
        assertThat(n.getSentAt()).isNotNull();
        verifyNoInteractions(emailService);
        verify(queueRepository).save(n);
    }

    @Test
    @DisplayName("EMAIL notifications use the template subject and payload message")
    void emailSentWithTemplateSubject() {
        NotificationQueue n = notification(2L, user, NotificationQueue.Channel.EMAIL, "LEAVE_APPROVED",
                "{\"title\":\"Approved!\",\"message\":\"Your leave from 1 Jan was approved\"}");
        queue(n);

        service.processQueuedNotifications();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(eq("asha@lms.com"), eq("Leave Request Approved"), body.capture());
        assertThat(body.getValue()).contains("Your leave from 1 Jan was approved").contains("Dear Employee 1");
        assertThat(n.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.SENT);
    }

    @Test
    void unknownTemplateFallsBackToPayloadTitle() {
        NotificationQueue n = notification(3L, user, NotificationQueue.Channel.EMAIL, "PROFILE_UPDATED",
                "{\"title\":\"Profile Updated\",\"message\":\"done\"}");
        queue(n);

        service.processQueuedNotifications();

        verify(emailService).sendHtmlEmail(eq("asha@lms.com"), eq("Profile Updated"), anyString());
    }

    @Test
    void invalidPayloadStillSendsWithDefaults() {
        NotificationQueue n = notification(4L, user, NotificationQueue.Channel.EMAIL, "SOMETHING", "not json");
        queue(n);

        service.processQueuedNotifications();

        verify(emailService).sendHtmlEmail(eq("asha@lms.com"), eq("Notification"), anyString());
    }

    @Test
    @DisplayName("user without an email address -> FAILED, no email attempted")
    void missingEmailMarksFailed() {
        user.setEmail(" ");
        NotificationQueue n = notification(5L, user, NotificationQueue.Channel.EMAIL, "LEAVE_APPROVED", "{}");
        queue(n);

        service.processQueuedNotifications();

        assertThat(n.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.FAILED);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("a send failure keeps the item QUEUED and increments the retry count")
    void failureIsRetried() {
        NotificationQueue n = notification(6L, user, NotificationQueue.Channel.EMAIL, "LEAVE_APPROVED", "{}");
        doThrow(new RuntimeException("smtp")).when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());
        queue(n);

        service.processQueuedNotifications();

        assertThat(n.getRetryCount()).isEqualTo(1);
        assertThat(n.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.QUEUED);
    }

    @Test
    @DisplayName("the third failure marks the item FAILED")
    void thirdFailureMarksFailed() {
        NotificationQueue n = notification(7L, user, NotificationQueue.Channel.EMAIL, "LEAVE_APPROVED", "{}");
        n.setRetryCount(2);
        doThrow(new RuntimeException("smtp")).when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());
        queue(n);

        service.processQueuedNotifications();

        assertThat(n.getRetryCount()).isEqualTo(3);
        assertThat(n.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("BUG GUARD: a null template code crashes the switch and burns a retry")
    void nullTemplateCodeConsumesRetry() {
        NotificationQueue n = notification(8L, user, NotificationQueue.Channel.EMAIL, null, "{}");
        queue(n);

        service.processQueuedNotifications();

        assertThat(n.getRetryCount()).isEqualTo(1);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("one bad item does not stop the rest of the batch")
    void batchContinuesAfterFailure() {
        NotificationQueue bad = notification(9L, user, NotificationQueue.Channel.EMAIL, null, "{}");
        NotificationQueue good = notification(10L, user, NotificationQueue.Channel.IN_APP, "X", "{}");
        queue(bad, good);

        service.processQueuedNotifications();

        assertThat(good.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.SENT);
    }

    @Test
    void repositoryFailureIsSwallowed() {
        when(queueRepository.findByStatusAndScheduledAtBefore(any(), any())).thenThrow(new RuntimeException("db"));
        service.processQueuedNotifications(); // must not throw
    }
}
