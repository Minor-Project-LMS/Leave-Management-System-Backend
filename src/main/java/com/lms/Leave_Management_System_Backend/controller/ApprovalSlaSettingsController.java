package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.ApprovalSlaSettingsDto;
import com.lms.Leave_Management_System_Backend.model.ApprovalSlaSettings;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.ApprovalSlaSettingsRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.ApprovalSlaService;
import com.lms.Leave_Management_System_Backend.service.LeaveEscalationService;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

// Deliberately its own small controller/table rather than folded into
// SettingsController — see the comment on WorkWeekSettings for why; same
// reasoning applies here.
@RestController
@RequestMapping("/api/v1/settings/approval-sla")
public class ApprovalSlaSettingsController {

    private final ApprovalSlaSettingsRepository repository;
    private final ApprovalSlaService approvalSlaService;
    private final LeaveEscalationService leaveEscalationService;
    private final UserRepository userRepository;

    public ApprovalSlaSettingsController(
            ApprovalSlaSettingsRepository repository,
            ApprovalSlaService approvalSlaService,
            LeaveEscalationService leaveEscalationService,
            UserRepository userRepository) {
        this.repository = repository;
        this.approvalSlaService = approvalSlaService;
        this.leaveEscalationService = leaveEscalationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApprovalSlaSettingsDto> getSlaSettings() {
        return ResponseEntity.ok(toDto(approvalSlaService.getSettings()));
    }

    @PatchMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<ApprovalSlaSettingsDto> updateSlaSettings(
            @RequestBody ApprovalSlaSettingsDto request,
            Authentication authentication) {

        if (request.getSlaDays() < 1) {
            throw new com.lms.Leave_Management_System_Backend.exception.BusinessRuleException(
                    "SLA must be at least 1 day.");
        }

        if (request.getUrgentWindowDays() != null && request.getUrgentWindowDays() < 0) {
            throw new com.lms.Leave_Management_System_Backend.exception.BusinessRuleException(
                    "Urgent window cannot be negative.");
        }
        if (request.getEscalationGraceDays() != null && request.getEscalationGraceDays() < 0) {
            throw new com.lms.Leave_Management_System_Backend.exception.BusinessRuleException(
                    "Escalation grace period cannot be negative.");
        }

        ApprovalSlaSettings settings = approvalSlaService.getSettings();
        settings.setSlaDays(request.getSlaDays());
        if (request.getUrgentWindowDays() != null) {
            settings.setUrgentWindowDays(request.getUrgentWindowDays());
        }
        if (request.getEscalationGraceDays() != null) {
            settings.setEscalationGraceDays(request.getEscalationGraceDays());
        }
        settings.setUpdatedAt(LocalDateTime.now());

        if (authentication != null) {
            userRepository.findByEmailIgnoreCase(authentication.getName())
                    .map(User::getId)
                    .ifPresent(settings::setUpdatedBy);
        }

        repository.save(settings);

        return ResponseEntity.ok(toDto(settings));
    }

    // Lets HR confirm this is actually configured correctly without
    // waiting until 2am for the scheduled run — also doubles as the only
    // practical way to exercise this end-to-end outside of Spring's own
    // cron infrastructure.
    @PostMapping("/run-now")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Integer>> runEscalationNow() {
        LeaveEscalationService.EscalationResult result = leaveEscalationService.runEscalationNow();
        return ResponseEntity.ok(Map.of(
                "escalatedCount", result.escalated(),
                "managersNotifiedCount", result.managersNotified(),
                "deferredNotUrgentCount", result.deferredNotUrgent()));
    }

    private ApprovalSlaSettingsDto toDto(ApprovalSlaSettings settings) {
        ApprovalSlaSettingsDto dto = new ApprovalSlaSettingsDto();
        dto.setSlaDays(settings.getSlaDays());
        dto.setUrgentWindowDays(settings.getUrgentWindowDays());
        dto.setEscalationGraceDays(settings.getEscalationGraceDays());
        return dto;
    }
}
