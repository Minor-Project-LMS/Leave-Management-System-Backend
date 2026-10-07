package com.lms.Leave_Management_System_Backend.service;

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

import java.time.LocalDateTime;
import java.util.List;
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
@Service
public class LeaveEscalationService {

    private static final Logger log = LoggerFactory.getLogger(LeaveEscalationService.class);

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

    @Transactional
    public int runEscalationNow() {
        int slaDays = approvalSlaService.getSlaDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(slaDays);

        List<LeaveRequest> overdue = leaveRequestRepository.findByStatusInAndAppliedAtBeforeAndEscalatedFalse(
                List.of(LeaveRequest.RequestStatus.PENDING_L1, LeaveRequest.RequestStatus.PENDING_L2), cutoff);

        if (overdue.isEmpty()) {
            log.info("LeaveEscalationService: no overdue approvals to escalate (SLA = {} day(s))", slaDays);
            return 0;
        }

        Optional<User> hrAdmin = userRepository.findFirstByRole_RoleCode("HR_ADMIN");
        if (hrAdmin.isEmpty()) {
            log.warn("LeaveEscalationService: {} overdue approval(s) found but no active HR_ADMIN to escalate to",
                    overdue.size());
            return 0;
        }

        int escalatedCount = 0;
        for (LeaveRequest request : overdue) {
            // A request can be missing a current approver (e.g. data
            // inconsistency) — skip rather than escalate "from nobody".
            if (request.getCurrentApprover() == null) continue;
            if (request.getCurrentApprover().getId().equals(hrAdmin.get().getId())) continue; // already HR's

            escalate(request, hrAdmin.get());
            escalatedCount++;
        }

        log.info("LeaveEscalationService: escalated {} of {} overdue approval(s) to {}",
                escalatedCount, overdue.size(), hrAdmin.get().getFullName());
        return escalatedCount;
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
            NotificationQueue notification = new NotificationQueue();
            notification.setUser(user);
            notification.setChannel(NotificationQueue.Channel.IN_APP);
            notification.setTemplateCode(type);
            notification.setPayload("{\"title\":\"" + title + "\",\"message\":\"" + message + "\"}");
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
