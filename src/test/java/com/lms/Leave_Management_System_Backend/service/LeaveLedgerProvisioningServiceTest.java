package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaveLedgerProvisioningServiceTest {

    private LeaveLedgerRepository ledgerRepository;
    private LeaveCategoryRepository categoryRepository;
    private LeaveLedgerProvisioningService service;
    private User user;
    private LeaveCategory casual;

    @BeforeEach
    void setUp() {
        ledgerRepository = mock(LeaveLedgerRepository.class);
        categoryRepository = mock(LeaveCategoryRepository.class);
        service = new LeaveLedgerProvisioningService(ledgerRepository, categoryRepository);
        user = employee(1L, "emp@lms.com");
        casual = category(1, "Casual Leave");
    }

    @Test
    @DisplayName("returns the existing rows untouched when every active category already has one")
    void nothingToCreate() {
        List<LeaveLedger> existing = List.of(ledger(user, casual, 2030, "10"));
        when(ledgerRepository.findByUserIdAndFiscalYear(1L, 2030)).thenReturn(existing);
        when(categoryRepository.findAll()).thenReturn(List.of(casual));

        assertThat(service.getOrInitializeLedger(user, 2030)).isSameAs(existing);
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("creates a missing row: opening = default quota, plus carry-forward from last year")
    void createsWithCarryForward() {
        when(ledgerRepository.findByUserIdAndFiscalYear(1L, 2030)).thenReturn(List.of(), List.of(new LeaveLedger()));
        when(categoryRepository.findAll()).thenReturn(List.of(casual));
        LeaveLedger lastYear = ledger(user, casual, 2029, "3");
        when(ledgerRepository.findByUserIdAndCategoryIdAndFiscalYear(1L, 1, 2029)).thenReturn(Optional.of(lastYear));

        List<LeaveLedger> result = service.getOrInitializeLedger(user, 2030);

        ArgumentCaptor<LeaveLedger> captor = ArgumentCaptor.forClass(LeaveLedger.class);
        verify(ledgerRepository).save(captor.capture());
        LeaveLedger created = captor.getValue();
        assertThat(created.getOpeningBalance()).isEqualByComparingTo("12");
        assertThat(created.getCarriedForward()).isEqualByComparingTo("3");
        assertThat(created.getClosingBalance()).isEqualByComparingTo("15");
        assertThat(created.getUsed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.getFiscalYear()).isEqualTo(2030);
        assertThat(created.getTransactionDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(created.getTransactionType()).isEqualTo("SYSTEM");
        assertThat(result).hasSize(1); // re-read after creating
    }

    @Test
    void nullQuotaAndNoHistoryGivesZeroBalance() {
        casual.setDefaultAnnualQuota(null);
        when(ledgerRepository.findByUserIdAndFiscalYear(1L, 2030)).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of(casual));

        service.getOrInitializeLedger(user, 2030);

        ArgumentCaptor<LeaveLedger> captor = ArgumentCaptor.forClass(LeaveLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getClosingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void inactiveCategoriesAreSkippedButNullStatusCountsAsActive() {
        LeaveCategory inactive = category(2, "Old Leave");
        inactive.setStatus("INACTIVE");
        LeaveCategory noStatus = category(3, "Legacy Leave");
        noStatus.setStatus(null);
        when(ledgerRepository.findByUserIdAndFiscalYear(1L, 2030)).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of(inactive, noStatus));

        service.getOrInitializeLedger(user, 2030);

        ArgumentCaptor<LeaveLedger> captor = ArgumentCaptor.forClass(LeaveLedger.class);
        verify(ledgerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCategory().getId()).isEqualTo(3);
    }
}
