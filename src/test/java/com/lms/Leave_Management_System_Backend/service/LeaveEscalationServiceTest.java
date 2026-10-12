package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.model.ApprovalSlaSettings;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaveEscalationServiceTest {

    private LeaveRequestRepository leaveRequestRepository;
    private UserRepository userRepository;
    private NotificationQueueRepository notificationRepository;
    private ApprovalSlaService slaService;
    private LeaveEscalationService service;

    private User employee;
    private User manager;
    private User hr;
    private LeaveCategory casual;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        userRepository = mock(UserRepository.class);
        notificationRepository = mock(NotificationQueueRepository.class);
        slaService = mock(ApprovalSlaService.class);
        service = new LeaveEscalationService(leaveRequestRepository, userRepository, notificationRepository, slaService);

        when(slaService.getSettings()).thenReturn(new ApprovalSlaSettings()); // sla 3, urgent 7, grace 1
        employee = employee(1L, "emp@lms.com");
        manager = manager(2L, "mgr@lms.com");
        hr = hr(3L, "hr@lms.com");
        casual = category(1, "Casual Leave");
    }

    private LeaveRequest pending(Long id, LocalDate start) {
        LeaveRequest r = leaveRequest(id, employee, casual, start, start.plusDays(1), "2",
                LeaveRequest.RequestStatus.PENDING_L1);
        r.setCurrentApprover(manager);
        r.setAppliedAt(LocalDateTime.now().minusDays(10));
        return r;
    }

    private void overdue(LeaveRequest... requests) {
        when(leaveRequestRepository.findByStatusInAndAppliedAtBeforeAndEscalatedFalse(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(requests));
        when(userRepository.findFirstByRole_RoleCode("HR_ADMIN")).thenReturn(Optional.of(hr));
    }

    @Nested
    class RunEscalation {

        @Test
        void nothingOverdue() {
            when(leaveRequestRepository.findByStatusInAndAppliedAtBeforeAndEscalatedFalse(anyList(), any()))
                    .thenReturn(List.of());

            LeaveEscalationService.EscalationResult result = service.runEscalationNow();

            assertThat(result).isEqualTo(new LeaveEscalationService.EscalationResult(0, 0, 0));
            verifyNoInteractions(userRepository);
        }

        @Test
        void noHrAdminToEscalateTo() {
            when(leaveRequestRepository.findByStatusInAndAppliedAtBeforeAndEscalatedFalse(anyList(), any()))
                    .thenReturn(List.of(pending(1L, LocalDate.now().plusDays(1))));
            when(userRepository.findFirstByRole_RoleCode("HR_ADMIN")).thenReturn(Optional.empty());

            assertThat(service.runEscalationNow()).isEqualTo(new LeaveEscalationService.EscalationResult(0, 0, 0));
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("stage 1: urgent overdue request -> HR notifies the manager first")
        void urgentRequestNotifiesManager() {
            LeaveRequest r = pending(1L, LocalDate.now().plusDays(2));
            overdue(r);

            LeaveEscalationService.EscalationResult result = service.runEscalationNow();

            assertThat(result.managersNotified()).isEqualTo(1);
            assertThat(result.escalated()).isZero();
            assertThat(r.isHrNotified()).isTrue();
            assertThat(r.getHrNotifiedBy()).isNull(); // system-triggered
            assertThat(r.getCurrentApprover()).isSameAs(manager);
            ArgumentCaptor<NotificationQueue> n = ArgumentCaptor.forClass(NotificationQueue.class);
            verify(notificationRepository).save(n.capture());
            assertThat(n.getValue().getUser()).isSameAs(manager);
            assertThat(n.getValue().getTemplateCode()).isEqualTo("LEAVE_HR_REMINDER");
        }

        @Test
        @DisplayName("stage 2: manager ignored the reminder past the grace period -> reassigned to HR")
        void escalatesAfterGracePeriod() {
            LeaveRequest r = pending(1L, LocalDate.now().plusDays(2));
            r.setHrNotifiedAt(LocalDateTime.now().minusDays(3));
            overdue(r);

            LeaveEscalationService.EscalationResult result = service.runEscalationNow();

            assertThat(result.escalated()).isEqualTo(1);
            assertThat(r.isEscalated()).isTrue();
            assertThat(r.getEscalatedAt()).isNotNull();
            assertThat(r.getCurrentApprover()).isSameAs(hr);
            assertThat(r.getOriginalApprover()).isSameAs(manager);
            // manager, HR and employee are all told
            verify(notificationRepository, times(3)).save(any(NotificationQueue.class));
        }

        @Test
        void withinGracePeriodNothingHappens() {
            LeaveRequest r = pending(1L, LocalDate.now().plusDays(2));
            r.setHrNotifiedAt(LocalDateTime.now().minusHours(2));
            overdue(r);

            assertThat(service.runEscalationNow()).isEqualTo(new LeaveEscalationService.EscalationResult(0, 0, 0));
            assertThat(r.isEscalated()).isFalse();
        }

        @Test
        @DisplayName("leave starting far in the future is deferred, not escalated")
        void nonUrgentDeferred() {
            LeaveRequest r = pending(1L, LocalDate.now().plusDays(30));
            overdue(r);

            assertThat(service.runEscalationNow().deferredNotUrgent()).isEqualTo(1);
            verify(leaveRequestRepository, never()).save(any());
        }

        @Test
        void skipsRequestsWithoutApproverOrAlreadyWithHr() {
            LeaveRequest noApprover = pending(1L, LocalDate.now());
            noApprover.setCurrentApprover(null);
            LeaveRequest alreadyHr = pending(2L, LocalDate.now());
            alreadyHr.setCurrentApprover(hr);
            overdue(noApprover, alreadyHr);

            assertThat(service.runEscalationNow()).isEqualTo(new LeaveEscalationService.EscalationResult(0, 0, 0));
        }

        @Test
        void scheduledRunDelegatesToRunNow() {
            when(leaveRequestRepository.findByStatusInAndAppliedAtBeforeAndEscalatedFalse(anyList(), any()))
                    .thenReturn(List.of());
            service.scheduledEscalation();
            verify(leaveRequestRepository).findByStatusInAndAppliedAtBeforeAndEscalatedFalse(anyList(), any());
        }
    }

    @Nested
    class NotifyManager {

        @Test
        void customMessageIsTrimmedAndRecorded() {
            LeaveRequest r = pending(1L, LocalDate.now().plusDays(1));

            service.notifyManager(r, hr, "  Please approve today  ");

            assertThat(r.getHrNotificationMessage()).isEqualTo("Please approve today");
            assertThat(r.getHrNotifiedBy()).isSameAs(hr);
            verify(leaveRequestRepository).save(r);
        }

        @Test
        void defaultMessageMentionsEmployeeAndDate() {
            LeaveRequest r = pending(1L, LocalDate.of(2030, 1, 7));

            service.notifyManager(r, hr, null);

            assertThat(r.getHrNotificationMessage())
                    .startsWith("Employee 1's leave starting 2030-01-07")
                    .contains("escalated to HR");
        }

        @Test
        void longMessageIsTruncatedTo500() {
            LeaveRequest r = pending(1L, LocalDate.now());
            service.notifyManager(r, hr, "x".repeat(800));
            assertThat(r.getHrNotificationMessage()).hasSize(500);
        }

        @Test
        void onlyPendingRequests() {
            LeaveRequest r = pending(1L, LocalDate.now());
            r.setStatus(LeaveRequest.RequestStatus.APPROVED);
            assertThatThrownBy(() -> service.notifyManager(r, hr, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Only pending requests");
        }

        @Test
        void requiresCurrentApprover() {
            LeaveRequest r = pending(1L, LocalDate.now());
            r.setCurrentApprover(null);
            assertThatThrownBy(() -> service.notifyManager(r, hr, null))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("current approver");
        }

        @Test
        void notificationFailureDoesNotBreakTheFlow() {
            when(notificationRepository.save(any())).thenThrow(new RuntimeException("db"));
            LeaveRequest r = pending(1L, LocalDate.now());

            service.notifyManager(r, hr, "ping");

            assertThat(r.isHrNotified()).isTrue();
        }
    }

    @Test
    void urgencyHelpers() {
        assertThat(service.urgentUntil()).isEqualTo(LocalDate.now().plusDays(7));

        LocalDate until = LocalDate.of(2030, 1, 10);
        LeaveRequest r = new LeaveRequest();
        r.setStartDate(LocalDate.of(2030, 1, 10));
        assertThat(LeaveEscalationService.isUrgent(r, until)).isTrue();
        r.setStartDate(LocalDate.of(2030, 1, 11));
        assertThat(LeaveEscalationService.isUrgent(r, until)).isFalse();
        r.setStartDate(null);
        assertThat(LeaveEscalationService.isUrgent(r, until)).isFalse();
    }
}
