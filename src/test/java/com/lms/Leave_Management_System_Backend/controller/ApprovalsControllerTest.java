package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.model.LeaveApproval;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.ApprovalDelegationRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveApprovalRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.LeaveEscalationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApprovalsControllerTest {

    private LeaveRequestRepository requests;
    private UserRepository users;
    private ApprovalDelegationRepository delegations;
    private LeaveApprovalRepository approvals;
    private LeaveEscalationService escalation;
    private MockMvc mvc;
    private User manager;
    private User employee;
    private LeaveCategory casual;

    @BeforeEach
    void setUp() {
        requests = mock(LeaveRequestRepository.class);
        users = mock(UserRepository.class);
        delegations = mock(ApprovalDelegationRepository.class);
        approvals = mock(LeaveApprovalRepository.class);
        escalation = mock(LeaveEscalationService.class);
        mvc = mockMvc(new ApprovalsController(requests, users, delegations, approvals, escalation));

        manager = manager(2L, "mgr@lms.com");
        employee = employee(1L, "emp@lms.com");
        casual = category(1, "Casual Leave");
        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager));
        when(escalation.urgentUntil()).thenReturn(LocalDate.of(2030, 1, 10));
    }

    private LeaveRequest req(Long id, LeaveRequest.RequestStatus status, int appliedDay, LocalDate start) {
        LeaveRequest r = leaveRequest(id, employee, casual, start, start, "1", status);
        r.setAppliedAt(LocalDateTime.of(2030, 1, appliedDay, 9, 0));
        r.setCurrentApprover(manager);
        return r;
    }

    private void inbox(List<LeaveRequest> all) {
        LeaveApproval decided = new LeaveApproval();
        decided.setRequest(all.get(0));
        when(approvals.findByApproverId(2L)).thenReturn(new ArrayList<>(List.of(decided)));
        when(requests.findByCurrentApproverId(eq(2L), any(Pageable.class))).thenReturn(new PageImpl<>(all));
        when(requests.findByOriginalApproverId(2L)).thenReturn(List.of());
        when(requests.findByIds(anyList())).thenReturn(all);
    }

    @Test
    @DisplayName("pending tab: HR-flagged requests first, then newest; counts cover every tab")
    void pendingInbox() throws Exception {
        LeaveRequest approved = req(1L, LeaveRequest.RequestStatus.APPROVED, 1, LocalDate.of(2030, 1, 5));
        LeaveRequest newer = req(2L, LeaveRequest.RequestStatus.PENDING_L1, 5, LocalDate.of(2030, 1, 20));
        LeaveRequest flagged = req(3L, LeaveRequest.RequestStatus.PENDING_L2, 2, LocalDate.of(2030, 1, 8));
        flagged.setHrNotifiedAt(LocalDateTime.of(2030, 1, 6, 9, 0));
        LeaveRequest rejected = req(4L, LeaveRequest.RequestStatus.REJECTED, 3, LocalDate.of(2030, 1, 5));
        LeaveRequest escalated = req(5L, LeaveRequest.RequestStatus.PENDING_L1, 4, LocalDate.of(2030, 1, 9));
        escalated.setEscalated(true);
        inbox(List.of(approved, newer, flagged, rejected, escalated));

        mvc.perform(get("/api/v1/approvals/inbox").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].hrNotified").value(true))
                .andExpect(jsonPath("$.data[0].hrNotifiedByName").value("HR"))
                .andExpect(jsonPath("$.data[0].urgent").value(true))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].urgent").value(false))
                .andExpect(jsonPath("$.data[2].id").value(5))
                .andExpect(jsonPath("$.counts.all").value(5))
                .andExpect(jsonPath("$.counts.pending").value(3))
                .andExpect(jsonPath("$.counts.approved").value(1))
                .andExpect(jsonPath("$.counts.rejected").value(1))
                .andExpect(jsonPath("$.counts.escalated").value(1));
    }

    @Test
    void otherTabsAndOldestSortAndPaging() throws Exception {
        LeaveRequest a = req(1L, LeaveRequest.RequestStatus.APPROVED, 1, LocalDate.of(2030, 1, 5));
        LeaveRequest b = req(2L, LeaveRequest.RequestStatus.APPROVED, 3, LocalDate.of(2030, 1, 5));
        LeaveRequest c = req(3L, LeaveRequest.RequestStatus.REJECTED, 2, LocalDate.of(2030, 1, 5));
        c.setEscalated(true);
        inbox(List.of(a, b, c));

        mvc.perform(get("/api/v1/approvals/inbox").param("status", "APPROVED").param("sort", "oldest")
                        .principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
        mvc.perform(get("/api/v1/approvals/inbox").param("status", "REJECTED").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/approvals/inbox").param("status", "ESCALATED").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data[0].id").value(3));
        mvc.perform(get("/api/v1/approvals/inbox").param("status", "ALL").param("limit", "2").param("page", "2")
                        .principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/approvals/inbox").param("status", "ALL").param("page", "9")
                        .principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/approvals/inbox").param("status", "BOGUS").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void hrAlerts() throws Exception {
        LeaveRequest flagged = req(3L, LeaveRequest.RequestStatus.PENDING_L1, 2, LocalDate.of(2030, 1, 8));
        flagged.setHrNotifiedAt(LocalDateTime.now());
        flagged.setHrNotifiedBy(hr(9L, "hr@lms.com"));
        flagged.setHrNotificationMessage("please act");
        when(requests.findByCurrentApproverIdAndStatusInAndHrNotifiedAtIsNotNullOrderByStartDateAsc(eq(2L), anyList()))
                .thenReturn(List.of(flagged));

        mvc.perform(get("/api/v1/approvals/hr-alerts").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hrNotifiedByName").value("HR 9"))
                .andExpect(jsonPath("$[0].hrNotificationMessage").value("please act"));
    }

    @Test
    void hrNotifiesManager() throws Exception {
        User hrUser = hr(9L, "hr@lms.com");
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hrUser));
        LeaveRequest r = req(3L, LeaveRequest.RequestStatus.PENDING_L1, 2, LocalDate.of(2030, 1, 8));
        when(requests.findById(3L)).thenReturn(Optional.of(r));

        mvc.perform(post("/api/v1/approvals/3/notify-manager").principal(auth("hr@lms.com", "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "Nudge"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
        verify(escalation).notifyManager(r, hrUser, "Nudge");
    }

    @Test
    void notifyWithoutBodyUsesDefaultMessage() throws Exception {
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hr(9L, "hr@lms.com")));
        LeaveRequest r = req(3L, LeaveRequest.RequestStatus.PENDING_L1, 2, LocalDate.of(2030, 1, 8));
        when(requests.findById(3L)).thenReturn(Optional.of(r));

        mvc.perform(post("/api/v1/approvals/3/notify-manager").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isOk());
        verify(escalation).notifyManager(eq(r), any(), isNull());
    }

    @Test
    void notifyOnNonPendingIs409() throws Exception {
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hr(9L, "hr@lms.com")));
        LeaveRequest r = req(3L, LeaveRequest.RequestStatus.APPROVED, 2, LocalDate.of(2030, 1, 8));
        when(requests.findById(3L)).thenReturn(Optional.of(r));
        when(escalation.notifyManager(any(), any(), any())).thenThrow(new ConflictException("Only pending requests can be flagged"));

        mvc.perform(post("/api/v1/approvals/3/notify-manager").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void notifyUnknownRequestIs404() throws Exception {
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hr(9L, "hr@lms.com")));
        mvc.perform(post("/api/v1/approvals/99/notify-manager").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isNotFound());
    }
}
