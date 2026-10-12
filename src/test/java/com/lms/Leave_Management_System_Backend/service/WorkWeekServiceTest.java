package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.WorkWeekSettings;
import com.lms.Leave_Management_System_Backend.repository.WorkWeekSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkWeekServiceTest {

    private WorkWeekSettingsRepository repository;
    private WorkWeekService service;

    // 2030-01-04 Fri, 2030-01-05 Sat, 2030-01-06 Sun, 2030-01-07 Mon
    private static final LocalDate FRIDAY = LocalDate.of(2030, 1, 4);
    private static final LocalDate SATURDAY = LocalDate.of(2030, 1, 5);
    private static final LocalDate SUNDAY = LocalDate.of(2030, 1, 6);

    @BeforeEach
    void setUp() {
        repository = mock(WorkWeekSettingsRepository.class);
        service = new WorkWeekService(repository);
    }

    @Test
    void createsDefaultSettingsWhenMissing() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.save(any(WorkWeekSettings.class))).thenAnswer(i -> i.getArgument(0));

        WorkWeekSettings settings = service.getSettings();

        assertThat(settings.isSaturdayWorking()).isFalse();
        verify(repository).save(any(WorkWeekSettings.class));
    }

    @Test
    void fiveDayWeek() {
        when(repository.findById(1)).thenReturn(Optional.of(new WorkWeekSettings()));

        assertThat(service.isWorkingDay(FRIDAY)).isTrue();
        assertThat(service.isWorkingDay(SATURDAY)).isFalse();
        assertThat(service.isWorkingDay(SUNDAY)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void sixDayWeekMakesSaturdayWorkingButNeverSunday() {
        WorkWeekSettings settings = new WorkWeekSettings();
        settings.setSaturdayWorking(true);
        when(repository.findById(1)).thenReturn(Optional.of(settings));

        assertThat(service.isSaturdayWorking()).isTrue();
        assertThat(service.isWorkingDay(SATURDAY)).isTrue();
        assertThat(service.isWorkingDay(SUNDAY)).isFalse();
    }
}
