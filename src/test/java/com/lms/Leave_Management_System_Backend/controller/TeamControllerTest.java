package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.TeamLeaveSummary;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamControllerTest {

    private UserRepository users;
    private LeaveRequestRepository requests;
    private LeaveLedgerRepository ledgers;
    private MockMvc mvc;
    private User manager;
    private User hrUser;
    private User report;
    private LeaveCategory casual;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        requests = mock(LeaveRequestRepository.class);
        ledgers = mock(LeaveLedgerRepository.class);
        mvc = mockMvc(new TeamController(users, requests, ledgers));
        manager = manager(2L, "mgr@lms.com");
        hrUser = hr(3L, "hr@lms.com");
        report = employee(1L, "emp@lms.com");
        report.setDepartment(department(10, "Engineering"));
        casual = category(1, "Casual Leave");
        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager));
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hrUser));
    }

    @Test
    @DisplayName("managers query only their reports; search text is trimmed")
    void membersForManager() throws Exception {
        when(users.findTeamMembers(eq(true), eq(2L), isNull(), eq("asha"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/team/members").param("q", "  asha ").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].departmentName").value("Engineering"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page.page").value(1))
                .andExpect(jsonPath("$.page.limit").value(20));
    }

    @Test
    void membersForHrAcrossOrg() throws Exception {
        when(users.findTeamMembers(eq(false), eq(3L), eq(10), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/team/members").param("departmentId", "10").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("calendar spreads a leave across each day it covers, clipped to the month, weekends hidden")
    void calendar() throws Exception {
        when(users.findByReportsToId(2L)).thenReturn(List.of(report));
        // Jan 2030: leave from 30 Dec 2029 to Fri 4 Jan 2030
        LeaveRequest leave = leaveRequest(1L, report, casual, LocalDate.of(2029, 12, 30), LocalDate.of(2030, 1, 4), "5",
                LeaveRequest.RequestStatus.APPROVED);
        when(requests.findApprovedLeavesForUsers(List.of(1L), LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31)))
                .thenReturn(List.of(leave));

        mvc.perform(get("/api/v1/team/calendar").param("month", "1").param("year", "2030")
                        .principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(23))                 // weekdays in Jan 2030
                .andExpect(jsonPath("$[0].date").exists())
                .andExpect(jsonPath("$[0].entries[0].fullName").value("Employee 1"))
                .andExpect(jsonPath("$[0].entries[0].categoryName").value("Casual Leave"))
                .andExpect(jsonPath("$[3].entries.length()").value(1))      // Fri 4 Jan
                .andExpect(jsonPath("$[4].entries.length()").value(0));     // Mon 7 Jan
    }

    @Test
    void calendarWithWeekendsAndCategoryFilter() throws Exception {
        when(users.findAll()).thenReturn(List.of(report));
        when(requests.findApprovedLeavesForUsers(anyList(), any(), any())).thenReturn(List.of(
                leaveRequest(1L, report, casual, LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 2), "1",
                        LeaveRequest.RequestStatus.APPROVED)));

        mvc.perform(get("/api/v1/team/calendar").param("month", "1").param("year", "2030")
                        .param("showWeekends", "true").param("categoryId", "99")
                        .principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(31))
                .andExpect(jsonPath("$[1].entries.length()").value(0));   // filtered out by category
    }

    @Test
    void calendarIsEmptyWhenNoEligibleUsers() throws Exception {
        when(users.findByReportsToId(2L)).thenReturn(List.of(report));

        mvc.perform(get("/api/v1/team/calendar").param("month", "1").param("year", "2030").param("departmentId", "99")
                        .principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(requests);
    }

    @Test
    void leaveSummary() throws Exception {
        when(users.findByReportsToId(2L)).thenReturn(List.of(report));
        when(ledgers.findTeamLeaveSummary(List.of(1L), 2030))
                .thenReturn(List.of(new TeamLeaveSummary(1, "Casual Leave", 6.0)));

        mvc.perform(get("/api/v1/team/leave-summary").param("year", "2030").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Casual Leave"))
                .andExpect(jsonPath("$[0].totalDays").value(6.0));
    }

    @Test
    void leaveSummaryEmptyTeam() throws Exception {
        mvc.perform(get("/api/v1/team/leave-summary").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
