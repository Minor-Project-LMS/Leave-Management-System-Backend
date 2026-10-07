package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.ApprovalSlaSettings;
import com.lms.Leave_Management_System_Backend.repository.ApprovalSlaSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class ApprovalSlaService {

    private final ApprovalSlaSettingsRepository repository;

    public ApprovalSlaService(ApprovalSlaSettingsRepository repository) {
        this.repository = repository;
    }

    // Lazily creates the single settings row (defaulting to 3 days) the
    // first time it's needed, rather than relying on seed data existing.
    public ApprovalSlaSettings getSettings() {
        return repository.findById(1)
                .orElseGet(() -> repository.save(new ApprovalSlaSettings()));
    }

    public int getSlaDays() {
        return getSettings().getSlaDays();
    }
}
