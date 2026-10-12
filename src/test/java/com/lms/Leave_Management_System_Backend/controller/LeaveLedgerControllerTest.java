package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeaveLedgerControllerTest {

    private LeaveLedgerRepository ledgerRepository;
    private UserRepository users;
    private LeaveLedgerProvisioningService provisioning;
    private MockMvc mvc;
    private User me;
    private LeaveCategory casual;

    @BeforeEach
    void setUp() {
        ledgerRepository = mock(LeaveLedgerRepository.class);
        users = mock(UserRepository.class);
        provisioning = mock(LeaveLedgerProvisioningService.class);
        mvc = mockMvc(new LeaveLedgerController(ledgerRepository, users, provisioning));
        me = employee(1L, "emp@lms.com");
        casual = category(1, "Casual Leave");
        when(users.findByEmailIgnoreCase("emp@lms.com")).thenReturn(Optional.of(me));
    }

    @Test
    void summaryForCurrentUserDefaultsToThisYear() throws Exception {
        LeaveLedger row = ledger(me, casual, LocalDate.now().getYear(), "8.5");
        row.setUsed(new BigDecimal("3.5"));
        when(provisioning.getOrInitializeLedger(me, LocalDate.now().getYear())).thenReturn(List.of(row));

        mvc.perform(get("/api/v1/leave-ledger").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Casual Leave"))
                .andExpect(jsonPath("$[0].used").value(3.5))
                .andExpect(jsonPath("$[0].closingBalance").value(8.5))
                .andExpect(jsonPath("$[0].availableBalance").value(8.5));
    }

    @Test
    void summaryForAnotherUserAndYear() throws Exception {
        User other = employee(5L, "other@lms.com");
        when(users.findById(5L)).thenReturn(Optional.of(other));
        when(provisioning.getOrInitializeLedger(other, 2029)).thenReturn(List.of(ledger(other, casual, 2029, "2")));

        mvc.perform(get("/api/v1/leave-ledger").param("userId", "5").param("year", "2029")
                        .principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fiscalYear").value(2029));
    }

    @Test
    void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/v1/leave-ledger").param("userId", "99").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void transactionsMapCreditDebitAndOther() throws Exception {
        LeaveLedger credit = ledger(me, casual, 2030, "10");
        credit.setTransactionType("CREDIT");
        credit.setAccrued(new BigDecimal("1"));
        credit.setReferenceId(77L);
        LeaveLedger debit = ledger(me, casual, 2030, "8");
        debit.setTransactionType("DEBIT");
        debit.setUsed(new BigDecimal("2"));
        LeaveLedger system = ledger(me, casual, 2030, "8");
        system.setTransactionType("SYSTEM");
        when(ledgerRepository.findWithFilters(eq(1L), eq(2030), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(credit, debit, system)));

        mvc.perform(get("/api/v1/leave-ledger/transactions").param("year", "2030").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].credit").value(1.0))
                .andExpect(jsonPath("$.data[0].debit").value(0.0))
                .andExpect(jsonPath("$.data[0].referenceId").value(77))
                .andExpect(jsonPath("$.data[1].debit").value(2.0))
                .andExpect(jsonPath("$.data[2].credit").value(0.0))
                .andExpect(jsonPath("$.data[2].description").value("Balance update"));

        verify(provisioning).getOrInitializeLedger(me, 2030);
        verify(ledgerRepository).findWithFilters(1L, 2030, null,
                PageRequest.of(0, 20, Sort.by("transactionDate").descending()));
    }

    @Test
    void exportCsv() throws Exception {
        when(provisioning.getOrInitializeLedger(me, 2030)).thenReturn(List.of(ledger(me, casual, 2030, "8")));

        mvc.perform(get("/api/v1/leave-ledger/export").param("fiscalYear", "2030").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=ledger_2030.csv"))
                .andExpect(content().string(containsString("Category,Fiscal Year,Opening Balance")))
                .andExpect(content().string(containsString("Casual Leave,2030,8,0,0,0,0,8")));
    }
}
