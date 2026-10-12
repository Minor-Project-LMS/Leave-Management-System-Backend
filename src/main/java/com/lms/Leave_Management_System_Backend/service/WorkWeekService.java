package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.WorkWeekSettings;
import com.lms.Leave_Management_System_Backend.repository.WorkWeekSettingsRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class WorkWeekService {

    private final WorkWeekSettingsRepository repository;

    public WorkWeekService(WorkWeekSettingsRepository repository) {
        this.repository = repository;
    }

    // Lazily creates the single settings row (defaulting to Saturday NOT
    // being a working day) the first time it's needed, rather than relying
    // on seed data existing.
    public WorkWeekSettings getSettings() {
        return repository.findById(1)
                .orElseGet(() -> repository.save(new WorkWeekSettings()));
    }

    public boolean isSaturdayWorking() {
        return getSettings().isSaturdayWorking();
    }

    // Sunday is always a non-working day; Saturday depends on the org's
    // setting. Everything else (Mon-Fri) is always a working day.
    public boolean isWorkingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SUNDAY) return false;
        if (day == DayOfWeek.SATURDAY) return isSaturdayWorking();
        return true;
    }
}
