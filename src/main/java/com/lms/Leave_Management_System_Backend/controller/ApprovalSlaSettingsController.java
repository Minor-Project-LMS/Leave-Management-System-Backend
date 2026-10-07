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
        ApprovalSlaSettingsDto dto = new ApprovalSlaSettingsDto();
        dto.setSlaDays(approvalSlaService.getSlaDays());
        return ResponseEntity.ok(dto);
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

        ApprovalSlaSettings settings = approvalSlaService.getSettings();
        settings.setSlaDays(request.getSlaDays());
        settings.setUpdatedAt(LocalDateTime.now());

        if (authentication != null) {
            userRepository.findByEmailIgnoreCase(authentication.getName())
                    .map(User::getId)
                    .ifPresent(settings::setUpdatedBy);
        }

        repository.save(settings);

        ApprovalSlaSettingsDto dto = new ApprovalSlaSettingsDto();
        dto.setSlaDays(settings.getSlaDays());
        return ResponseEntity.ok(dto);
    }

    // Lets HR confirm this is actually configured correctly without
    // waiting until 2am for the scheduled run — also doubles as the only
    // practical way to exercise this end-to-end outside of Spring's own
    // cron infrastructure.
    @PostMapping("/run-now")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Integer>> runEscalationNow() {
        int escalatedCount = leaveEscalationService.runEscalationNow();
        return ResponseEntity.ok(Map.of("escalatedCount", escalatedCount));
    }
}
