package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.WorkWeekSettingsDto;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.model.WorkWeekSettings;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.repository.WorkWeekSettingsRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.WorkWeekService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

// Deliberately its own small controller/table rather than folded into
// SettingsController — see the comment on WorkWeekSettings for why.
// Anyone signed in can read this (it affects what every employee's Apply
// Leave day-count preview should show), but only HR can change it.
@RestController
@RequestMapping("/api/v1/settings/work-week")
public class WorkWeekSettingsController {

    private final WorkWeekSettingsRepository repository;
    private final WorkWeekService workWeekService;
    private final UserRepository userRepository;

    public WorkWeekSettingsController(WorkWeekSettingsRepository repository,
                                       WorkWeekService workWeekService,
                                       UserRepository userRepository) {
        this.repository = repository;
        this.workWeekService = workWeekService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<WorkWeekSettingsDto> getWorkWeekSettings() {
        WorkWeekSettingsDto dto = new WorkWeekSettingsDto();
        dto.setSaturdayWorking(workWeekService.isSaturdayWorking());
        return ResponseEntity.ok(dto);
    }

    @PatchMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<WorkWeekSettingsDto> updateWorkWeekSettings(
            @RequestBody WorkWeekSettingsDto request,
            Authentication authentication) {

        WorkWeekSettings settings = workWeekService.getSettings();
        settings.setSaturdayWorking(request.isSaturdayWorking());
        settings.setUpdatedAt(LocalDateTime.now());

        if (authentication != null) {
            userRepository.findByEmailIgnoreCase(authentication.getName())
                    .map(User::getId)
                    .ifPresent(settings::setUpdatedBy);
        }

        repository.save(settings);

        WorkWeekSettingsDto dto = new WorkWeekSettingsDto();
        dto.setSaturdayWorking(settings.isSaturdayWorking());
        return ResponseEntity.ok(dto);
    }
}
