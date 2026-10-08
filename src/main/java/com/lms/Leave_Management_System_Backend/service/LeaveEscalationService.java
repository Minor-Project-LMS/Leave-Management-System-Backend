package com.lms.Leave_Management_System_Backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.model.ApprovalSlaSettings;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Approval SLA escalation: this is the involuntary counterpart to
// Delegation. Delegation is a manager proactively handing off their
// approvals while away; this catches the case where nobody did that and a
// request has just been sitting, forgotten, past the SLA window — it gets
// automatically reassigned to HR instead of waiting indefinitely.
//
// Reassigning currentApprover is all that's needed for it to show up in
// HR's existing approval inbox — the same query that already surfaces
// delegated approvals (WHERE currentApprover = :userId) picks these up too,
// with no separate HR-facing queue required.
//
// Only urgent requests escalate (see runEscalationNow), and never without
// the manager being notified by HR first.
@Service
public class LeaveEscalationService {

    private static final Logger log = LoggerFactory.getLogger(LeaveEscalationService.class);

    private static final List<LeaveRequest.RequestStatus> PENDING_STATUSES =
            List.of(LeaveRequest.RequestStatus.PENDING_L1, LeaveRequest.RequestStatus.PENDING_L2);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final NotificationQueueRepository notificationQueueRepository;
    private final ApprovalSlaService approvalSlaService;

    public LeaveEscalationService(
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            NotificationQueueRepository notificationQueueRepository,
            ApprovalSlaService approvalSlaService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.notificationQueueRepository = notificationQueueRepository;
        this.approvalSlaService = approvalSlaService;
    }

    // Runs once a day at 2am. Also callable directly (see
    // runEscalationNow() below, wired to an HR-only manual-trigger
    // endpoint) since waiting for 2am makes this hard to see work, both
    // for HR wanting to confirm it's configured correctly and for testing.
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledEscalation() {
        runEscalationNow();
    }

    public record EscalationResult(int escalated, int managersNotified, int deferredNotUrgent) {
    }

    // Overdue requests go through two stages, and only if they're urgent
    // (leave starts within the urgent window):
    //   1. HR notifies the manager first (unless HR already did manually).
    //   2. If the manager still hasn't acted after the grace period, the
    //      request is escalated to HR.
    // Non-urgent requests (e.g. leave next month) are left with the manager
    // — there's still time to approve them normally. They're re-checked on
    // every run, so they become eligible once the start date gets close.
    @Transactional
    public EscalationResult runEscalationNow() {
        ApprovalSlaSettings settings = approvalSlaService.getSettings();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(settings.getSlaDays());
        LocalDateTime graceCutoff = now.minusDays(settings.getEscalationGraceDays());

        List<LeaveRequest> overdue = leaveRequestRepository.findByStatusInAndAppliedAtBeforeAndEscalatedFalse(
                PENDING_STATUSES, cutoff);

        if (overdue.isEmpty()) {
            log.info("LeaveEscalationService: no overdue approvals (SLA = {} day(s))", settings.getSlaDays());
            return new EscalationResult(0, 0, 0);
        }

        Optional<User> hrAdmin = userRepository.findFirstByRole_RoleCode("HR_ADMIN");
        if (hrAdmin.isEmpty()) {
            log.warn("LeaveEscalationService: {} overdue approval(s) found but no active HR_ADMIN to escalate to",
                    overdue.size());
            return new EscalationResult(0, 0, 0);
        }

        int escalatedCount = 0;
        int notifiedCount = 0;
        int deferredCount = 0;
        for (LeaveRequest request : overdue) {
            // A request can be missing a current approver (e.g. data
            // inconsistency) — skip rather than escalate "from nobody".
            if (request.getCurrentApprover() == null) continue;
            if (request.getCurrentApprover().getId().equals(hrAdmin.get().getId())) continue; // already HR's

            if (!isUrgent(request, settings)) {
                deferredCount++;
                continue;
            }

            if (!request.isHrNotified()) {
                notifyManager(request, null, null);
                notifiedCount++;
            } else if (request.getHrNotifiedAt().isBefore(graceCutoff)) {
                escalate(request, hrAdmin.get());
                escalatedCount++;
            }
        }

        log.info("LeaveEscalationService: {} overdue approval(s) — escalated {}, managers notified {}, "
                        + "deferred (not urgent) {}",
                overdue.size(), escalatedCount, notifiedCount, deferredCount);
        return new EscalationResult(escalatedCount, notifiedCount, deferredCount);
    }

    // Leave starting on or before this date counts as urgent. Exposed so
    // list endpoints can flag urgency without re-reading settings per row.
    public LocalDate urgentUntil() {
        return LocalDate.now().plusDays(approvalSlaService.getSettings().getUrgentWindowDays());
    }

    public static boolean isUrgent(LeaveRequest request, LocalDate urgentUntil) {
        return request.getStartDate() != null && !request.getStartDate().isAfter(urgentUntil);
    }

    private boolean isUrgent(LeaveRequest request, ApprovalSlaSettings settings) {
        return isUrgent(request, LocalDate.now().plusDays(settings.getUrgentWindowDays()));
    }

    // HR -> manager nudge. Called by the scheduler (notifiedBy = null) as
    // the step before escalation, or directly by HR from the approvals
    // screen. Either way it stamps the request so the manager's dashboard
    // pops it up and the inbox pins it to the top.
    @Transactional
    public LeaveRequest notifyManager(LeaveRequest request, User notifiedBy, String customMessage) {
        if (!PENDING_STATUSES.contains(request.getStatus())) {
            throw new ConflictException("Only pending requests can be flagged to the manager");
        }
        User manager = request.getCurrentApprover();
        if (manager == null) {
            throw new ConflictException("This request does not have a current approver");
        }

        String employeeName = request.getUser() != null ? request.getUser().getFullName() : "an employee";
        String message = customMessage != null && !customMessage.isBlank()
                ? customMessage.trim()
                : employeeName + "'s leave starting " + request.getStartDate()
                        + " is still awaiting your approval. Please act on it, otherwise it will be escalated to HR.";
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }

        request.setHrNotifiedAt(LocalDateTime.now());
        request.setHrNotifiedBy(notifiedBy);
        request.setHrNotificationMessage(message);
        leaveRequestRepository.save(request);

        notify(manager, "LEAVE_HR_REMINDER", "HR: action needed on a leave request", message, request.getId());
        return request;
    }

    private void escalate(LeaveRequest request, User hrAdmin) {
        User originalApprover = request.getCurrentApprover();

        request.setOriginalApprover(originalApprover);
        request.setCurrentApprover(hrAdmin);
        request.setEscalated(true);
        request.setEscalatedAt(LocalDateTime.now());
        leaveRequestRepository.save(request);

        String employeeName = request.getUser() != null ? request.getUser().getFullName() : "an employee";

        notify(originalApprover, "LEAVE_ESCALATED",
                "Leave request escalated",
                employeeName + "'s leave request has been pending for too long and was escalated to HR.",
                request.getId());

        notify(hrAdmin, "LEAVE_ESCALATED",
                "Approval escalated to you",
                employeeName + "'s leave request was reassigned to you after sitting unapproved past the SLA window.",
                request.getId());

        if (request.getUser() != null) {
            notify(request.getUser(), "LEAVE_ESCALATED",
                    "Your leave request was escalated",
                    "Your leave request is taking longer than expected, so it's now been routed to HR for a decision.",
                    request.getId());
        }

        // No AuditTrail entry here — performed_by is NOT NULL on that
        // table, and this is a system-driven event with no real user to
        // attribute it to. The request's own escalated/escalatedAt/
        // originalApprover fields already record what happened and when,
        // which covers this without forcing a misleading "performed by"
        // attribution onto HR or the original approver.
    }

    private void notify(User user, String type, String title, String message, Long requestId) {
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(Map.of("title", title, "message", message));
            NotificationQueue notification = new NotificationQueue();
            notification.setUser(user);
            notification.setChannel(NotificationQueue.Channel.IN_APP);
            notification.setTemplateCode(type);
            notification.setPayload(payload);
            notification.setRelatedEntityType("LEAVE_REQUEST");
            notification.setRelatedEntityId(requestId);
            notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setScheduledAt(LocalDateTime.now());
            notification.setIsRead(false);
            notificationQueueRepository.save(notification);
        } catch (Exception ex) {
            log.warn("LeaveEscalationService: failed to queue notification for user {}: {}",
                    user != null ? user.getId() : null, ex.getMessage());
        }
    }
}
