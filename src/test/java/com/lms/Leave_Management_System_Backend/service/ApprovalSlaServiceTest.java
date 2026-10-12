package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.ApprovalSlaSettings;
import com.lms.Leave_Management_System_Backend.repository.ApprovalSlaSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalSlaServiceTest {

    private final ApprovalSlaSettingsRepository repository = mock(ApprovalSlaSettingsRepository.class);
    private final ApprovalSlaService service = new ApprovalSlaService(repository);

    @Test
    void defaultsAreCreatedLazily() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.save(any(ApprovalSlaSettings.class))).thenAnswer(i -> i.getArgument(0));

        ApprovalSlaSettings settings = service.getSettings();

        assertThat(settings.getSlaDays()).isEqualTo(3);
        assertThat(settings.getUrgentWindowDays()).isEqualTo(7);
        assertThat(settings.getEscalationGraceDays()).isEqualTo(1);
        verify(repository).save(any(ApprovalSlaSettings.class));
    }

    @Test
    void returnsStoredSlaDays() {
        ApprovalSlaSettings stored = new ApprovalSlaSettings();
        stored.setSlaDays(5);
        when(repository.findById(1)).thenReturn(Optional.of(stored));

        assertThat(service.getSlaDays()).isEqualTo(5);
        verify(repository, never()).save(any());
    }
}
