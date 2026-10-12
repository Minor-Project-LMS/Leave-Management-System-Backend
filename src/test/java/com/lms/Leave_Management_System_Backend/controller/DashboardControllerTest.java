package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTest {

    private UserRepository users;
    private LeaveRequestRepository requests;
    private LeaveLedgerProvisioningService provisioning;
    private MockMvc mvc;
    private User me;
    private LeaveCategory casual;
    private LeaveCategory compOff;
    private LeaveCategory maternity;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        requests = mock(LeaveRequestRepository.class);
        provisioning = mock(LeaveLedgerProvisioningService.class);
        mvc = mockMvc(new DashboardController(users, requests, provisioning));

        me = employee(1L, "emp@lms.com");
        when(users.findByEmailIgnoreCase("emp@lms.com")).thenReturn(Optional.of(me));

        casual = category(1, "Casual Leave");
        compOff = category(2, "Comp Off");
        compOff.setCategoryType("COMPENSATORY");
        maternity = category(3, "Maternity Leave");
        maternity.setCategoryType("SPECIAL");

        LeaveLedger casualRow = ledger(me, casual, 2030, "8.25");
        casualRow.setUsed(new BigDecimal("4"));
        LeaveLedger compRow = ledger(me, compOff, 2030, "1.5");
        LeaveLedger maternityRow = ledger(me, maternity, 2030, "180");
        when(provisioning.getOrInitializeLedger(eq(me), anyInt())).thenReturn(List.of(casualRow, compRow, maternityRow));
    }

    @Test
    @DisplayName("summary excludes SPECIAL categories and reports comp-off separately")
    void summary() throws Exception {
        when(requests.findByUserIdAndStatus(1L, LeaveRequest.RequestStatus.PENDING_L1)).thenReturn(List.of(new LeaveRequest()));
        when(requests.findByUserIdAndStatus(1L, LeaveRequest.RequestStatus.PENDING_L2)).thenReturn(List.of(new LeaveRequest()));

        mvc.perform(get("/api/v1/dashboard/summary").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableLeave").value(9.8))   // 8.25 + 1.5 rounded to 1 dp
                .andExpect(jsonPath("$.usedLeave").value(4.0))
                .andExpect(jsonPath("$.compOffBalance").value(1.5))
                .andExpect(jsonPath("$.pendingRequests").value(2));
    }

    @Test
    void trendHasOneSeriesPerNonSpecialCategory() throws Exception {
        LeaveRequest jan = leaveRequest(1L, me, casual, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 11), "2",
                LeaveRequest.RequestStatus.APPROVED);
        LeaveRequest pending = leaveRequest(2L, me, casual, LocalDate.of(2030, 1, 20), LocalDate.of(2030, 1, 20), "1",
                LeaveRequest.RequestStatus.PENDING_L1);
        LeaveRequest lastYear = leaveRequest(3L, me, casual, LocalDate.of(2029, 1, 20), LocalDate.of(2029, 1, 20), "1",
                LeaveRequest.RequestStatus.APPROVED);
        when(requests.findByUserId(1L)).thenReturn(List.of(jan, pending, lastYear));

        mvc.perform(get("/api/v1/dashboard/leave-trend").param("year", "2030").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("Casual Leave"))
                .andExpect(jsonPath("$[0].color").value("#2563eb"))
                .andExpect(jsonPath("$[0].points.length()").value(12))
                .andExpect(jsonPath("$[0].points[0].month").value("Jan"))
                .andExpect(jsonPath("$[0].points[0].days").value(2.0))
                .andExpect(jsonPath("$[1].category").value("Comp Off"));
    }

    @Test
    void distributionListsUsedDaysPerCategory() throws Exception {
        mvc.perform(get("/api/v1/dashboard/leave-distribution").principal(auth("emp@lms.com", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].label").value("Casual Leave"))
                .andExpect(jsonPath("$[0].value").value(4.0))
                .andExpect(jsonPath("$[1].color").value("#16a34a"));
    }

    @Test
    void hrSummary() throws Exception {
        User a = employee(1L, "a@lms.com");
        User b = employee(2L, "b@lms.com");
        when(users.findAll()).thenReturn(List.of(a, b));
        when(users.findByEmploymentStatus(User.EmploymentStatus.ACTIVE)).thenReturn(List.of(a));
        when(users.findByEmploymentStatus(User.EmploymentStatus.SEPARATED)).thenReturn(List.of(b));
        LocalDate today = LocalDate.now();
        when(requests.findAll()).thenReturn(List.of(
                leaveRequest(1L, a, casual, today.minusDays(1), today.plusDays(1), "3", LeaveRequest.RequestStatus.APPROVED),
                leaveRequest(2L, a, casual, today, today, "1", LeaveRequest.RequestStatus.PENDING_L1),
                leaveRequest(3L, a, casual, today.plusDays(3), today.plusDays(4), "2", LeaveRequest.RequestStatus.APPROVED)));

        mvc.perform(get("/api/v1/dashboard/hr-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(2))
                .andExpect(jsonPath("$.activeEmployees").value(1))
                .andExpect(jsonPath("$.inactiveEmployees").value(1))
                .andExpect(jsonPath("$.onLeaveToday").value(1));
    }

    @Test
    void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary").principal(auth("ghost@lms.com", "EMPLOYEE")))
                .andExpect(status().isNotFound());
    }
}
