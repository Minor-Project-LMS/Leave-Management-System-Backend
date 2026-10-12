package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.ApprovalSlaSettingsDto;
import com.lms.Leave_Management_System_Backend.dto.WorkWeekSettingsDto;
import com.lms.Leave_Management_System_Backend.model.ApprovalSlaSettings;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.model.WorkWeekSettings;
import com.lms.Leave_Management_System_Backend.repository.*;
import com.lms.Leave_Management_System_Backend.service.ApprovalSlaService;
import com.lms.Leave_Management_System_Backend.service.LeaveEscalationService;
import com.lms.Leave_Management_System_Backend.service.WorkWeekService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Small controllers: roles lookup, settings, work-week, approval SLA,
 * and the manager dashboard/team/approval widgets.
 */
class SimpleControllersTest {

    @Nested
    class Lookups {
        @Test
        void listsRoles() throws Exception {
            RoleRepository roles = mock(RoleRepository.class);
            when(roles.findAll()).thenReturn(List.of(role("EMPLOYEE"), role("HR_ADMIN")));

            mockMvc(new LookupsController(roles)).perform(get("/api/v1/roles").principal(auth("a@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].roleCode").value("EMPLOYEE"))
                    .andExpect(jsonPath("$[1].roleCode").value("HR_ADMIN"));
        }
    }

    @Nested
    class Settings {
        private final MockMvc mvc = mockMvc(new SettingsController());

        @Test
        void returnsAllSections() throws Exception {
            mvc.perform(get("/api/v1/settings").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.general.companyName").value("Acme Corporation"))
                    .andExpect(jsonPath("$.leaveSettings.maxLeaveDays").value(30))
                    .andExpect(jsonPath("$.approvalWorkflow.twoStepApprovalEnabled").value(true))
                    .andExpect(jsonPath("$.systemPreferences").exists())
                    .andExpect(jsonPath("$.security.passwordMinLength").value(8))
                    .andExpect(jsonPath("$.systemInfo.version").value("1.0.0"));
        }

        @Test
        void patchEchoesBody() throws Exception {
            mvc.perform(patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("general", Map.of("companyName", "New Co")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.general.companyName").value("New Co"));
        }
    }

    @Nested
    class WorkWeek {
        private WorkWeekSettingsRepository repository;
        private WorkWeekService service;
        private UserRepository users;
        private MockMvc mvc;

        @BeforeEach
        void setUp() {
            repository = mock(WorkWeekSettingsRepository.class);
            service = mock(WorkWeekService.class);
            users = mock(UserRepository.class);
            mvc = mockMvc(new WorkWeekSettingsController(repository, service, users));
        }

        @Test
        void read() throws Exception {
            when(service.isSaturdayWorking()).thenReturn(true);
            mvc.perform(get("/api/v1/settings/work-week"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saturdayWorking").value(true));
        }

        @Test
        void updateRecordsWhoChangedIt() throws Exception {
            WorkWeekSettings settings = new WorkWeekSettings();
            when(service.getSettings()).thenReturn(settings);
            when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hr(9L, "hr@lms.com")));
            WorkWeekSettingsDto body = new WorkWeekSettingsDto();
            body.setSaturdayWorking(true);

            mvc.perform(patch("/api/v1/settings/work-week").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saturdayWorking").value(true));

            assertThat(settings.isSaturdayWorking()).isTrue();
            assertThat(settings.getUpdatedBy()).isEqualTo(9L);
            assertThat(settings.getUpdatedAt()).isNotNull();
            verify(repository).save(settings);
        }
    }

    @Nested
    class ApprovalSla {
        private ApprovalSlaSettingsRepository repository;
        private ApprovalSlaService slaService;
        private LeaveEscalationService escalationService;
        private UserRepository users;
        private MockMvc mvc;
        private ApprovalSlaSettings settings;

        @BeforeEach
        void setUp() {
            repository = mock(ApprovalSlaSettingsRepository.class);
            slaService = mock(ApprovalSlaService.class);
            escalationService = mock(LeaveEscalationService.class);
            users = mock(UserRepository.class);
            settings = new ApprovalSlaSettings();
            when(slaService.getSettings()).thenReturn(settings);
            mvc = mockMvc(new ApprovalSlaSettingsController(repository, slaService, escalationService, users));
        }

        private String body(int sla, Integer urgent, Integer grace) {
            ApprovalSlaSettingsDto dto = new ApprovalSlaSettingsDto();
            dto.setSlaDays(sla);
            dto.setUrgentWindowDays(urgent);
            dto.setEscalationGraceDays(grace);
            return json(dto);
        }

        @Test
        void read() throws Exception {
            mvc.perform(get("/api/v1/settings/approval-sla"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slaDays").value(3))
                    .andExpect(jsonPath("$.urgentWindowDays").value(7))
                    .andExpect(jsonPath("$.escalationGraceDays").value(1));
        }

        @Test
        void update() throws Exception {
            mvc.perform(patch("/api/v1/settings/approval-sla").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body(5, 10, 2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slaDays").value(5))
                    .andExpect(jsonPath("$.urgentWindowDays").value(10))
                    .andExpect(jsonPath("$.escalationGraceDays").value(2));
            verify(repository).save(settings);
        }

        @Test
        void partialUpdateKeepsOptionalFields() throws Exception {
            mvc.perform(patch("/api/v1/settings/approval-sla")
                            .contentType(MediaType.APPLICATION_JSON).content(body(4, null, null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.urgentWindowDays").value(7));
        }

        @Test
        void slaBelowOneIs422() throws Exception {
            mvc.perform(patch("/api/v1/settings/approval-sla")
                            .contentType(MediaType.APPLICATION_JSON).content(body(0, null, null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value("SLA must be at least 1 day."));
            verify(repository, never()).save(any());
        }

        @Test
        void negativeWindowsAre422() throws Exception {
            mvc.perform(patch("/api/v1/settings/approval-sla")
                            .contentType(MediaType.APPLICATION_JSON).content(body(3, -1, null)))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(patch("/api/v1/settings/approval-sla")
                            .contentType(MediaType.APPLICATION_JSON).content(body(3, null, -1)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void runNow() throws Exception {
            when(escalationService.runEscalationNow()).thenReturn(new LeaveEscalationService.EscalationResult(2, 3, 4));

            mvc.perform(post("/api/v1/settings/approval-sla/run-now"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.escalatedCount").value(2))
                    .andExpect(jsonPath("$.managersNotifiedCount").value(3))
                    .andExpect(jsonPath("$.deferredNotUrgentCount").value(4));
        }
    }

    @Nested
    class ManagerWidgets {
        private UserRepository users;
        private LeaveRequestRepository requests;
        private User manager;
        private User report;
        private LeaveCategory casual;

        @BeforeEach
        void setUp() {
            users = mock(UserRepository.class);
            requests = mock(LeaveRequestRepository.class);
            manager = manager(2L, "mgr@lms.com");
            report = employee(1L, "emp@lms.com");
            casual = category(1, "Casual Leave");
            when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager));
        }

        private LeaveRequest pending(Long id, LocalDate start, boolean hrNotified) {
            LeaveRequest r = leaveRequest(id, report, casual, start, start.plusDays(1), "2", LeaveRequest.RequestStatus.PENDING_L1);
            if (hrNotified) r.setHrNotifiedAt(LocalDateTime.now());
            return r;
        }

        @Test
        @DisplayName("pending approvals widget puts HR-flagged requests first and honours the limit")
        void pendingApprovals() throws Exception {
            when(requests.findByCurrentApproverIdAndStatus(2L, LeaveRequest.RequestStatus.PENDING_L1)).thenReturn(List.of(
                    pending(10L, LocalDate.of(2030, 1, 7), false),
                    pending(11L, LocalDate.of(2030, 2, 7), true),
                    pending(12L, LocalDate.of(2030, 1, 2), false)));
            when(requests.findByCurrentApproverIdAndStatus(2L, LeaveRequest.RequestStatus.PENDING_L2)).thenReturn(List.of());

            mockMvc(new ManagerApprovalsController(requests, users))
                    .perform(get("/api/v1/manager/approvals/pending").param("limit", "2").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value("LR-11"))
                    .andExpect(jsonPath("$[0].hrNotified").value(true))
                    .andExpect(jsonPath("$[1].id").value("LR-12"))
                    .andExpect(jsonPath("$[1].type").value("Casual Leave"))
                    .andExpect(jsonPath("$[1].days").value(2.0));
        }

        @Test
        void unknownManagerIs404() throws Exception {
            mockMvc(new ManagerApprovalsController(requests, users))
                    .perform(get("/api/v1/manager/approvals/pending").principal(auth("ghost@lms.com", "MANAGER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void dashboardSummary() throws Exception {
            User inactive = employee(3L, "gone@lms.com");
            inactive.setEmploymentStatus(User.EmploymentStatus.SEPARATED);
            when(users.findByReportsToId(2L)).thenReturn(List.of(report, inactive));
            when(requests.findByCurrentApproverIdAndStatus(2L, LeaveRequest.RequestStatus.PENDING_L1))
                    .thenReturn(List.of(pending(10L, LocalDate.now(), true), pending(11L, LocalDate.now(), false)));
            when(requests.findByCurrentApproverIdAndStatus(2L, LeaveRequest.RequestStatus.PENDING_L2))
                    .thenReturn(List.of(pending(12L, LocalDate.now(), false), pending(13L, LocalDate.now(), false)));
            when(requests.findByUserIdAndStatusAndStartDateBetween(eq(1L), eq(LeaveRequest.RequestStatus.APPROVED), any(), any()))
                    .thenReturn(List.of(new LeaveRequest()));

            mockMvc(new ManagerDashboardController(users, requests))
                    .perform(get("/api/v1/manager/dashboard/summary").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTeamSize").value(2))
                    .andExpect(jsonPath("$.activeEmployees").value(1))
                    .andExpect(jsonPath("$.pendingApprovals").value(4))
                    .andExpect(jsonPath("$.pendingUrgent").value(true))
                    .andExpect(jsonPath("$.hrNotifiedCount").value(1))
                    .andExpect(jsonPath("$.leavesThisMonth").value(1));
        }

        @Test
        void dashboardStaticCharts() throws Exception {
            MockMvc mvc = mockMvc(new ManagerDashboardController(users, requests));
            mvc.perform(get("/api/v1/manager/dashboard/leave-trend"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
            mvc.perform(get("/api/v1/manager/dashboard/leave-distribution"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        }

        @Test
        void teamOverviewAndUpcoming() throws Exception {
            MockMvc mvc = mockMvc(new ManagerTeamController(users, requests));
            mvc.perform(get("/api/v1/manager/team/leave-overview"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
            mvc.perform(get("/api/v1/manager/team/upcoming-leaves"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        }
    }
}
