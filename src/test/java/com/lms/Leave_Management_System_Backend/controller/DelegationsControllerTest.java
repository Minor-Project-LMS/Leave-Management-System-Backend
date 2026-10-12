package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.ApprovalDelegation;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.ApprovalDelegationRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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

class DelegationsControllerTest {

    private ApprovalDelegationRepository delegations;
    private UserRepository users;
    private AttachmentService attachments;
    private MockMvc mvc;
    private User manager;
    private User hrUser;

    @BeforeEach
    void setUp() {
        delegations = mock(ApprovalDelegationRepository.class);
        users = mock(UserRepository.class);
        attachments = mock(AttachmentService.class);
        mvc = mockMvc(new DelegationsController(delegations, users, attachments));
        manager = manager(2L, "mgr@lms.com");
        hrUser = hr(3L, "hr@lms.com");
        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager));
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hrUser));
        when(users.findById(3L)).thenReturn(Optional.of(hrUser));
        when(attachments.resolveAvatarUrl(anyLong())).thenReturn("/avatar.png");
        when(delegations.save(any(ApprovalDelegation.class))).thenAnswer(inv -> {
            ApprovalDelegation d = inv.getArgument(0);
            if (d.getId() == null) d.setId(50);
            return d;
        });
    }

    private ApprovalDelegation delegation(Integer id, LocalDate start, LocalDate end, boolean active) {
        ApprovalDelegation d = new ApprovalDelegation();
        d.setId(id);
        d.setDelegator(manager);
        d.setDelegate(hrUser);
        d.setStartDate(start);
        d.setEndDate(end);
        d.setActive(active);
        return d;
    }

    @Test
    @DisplayName("eligible delegates are HR admins other than the caller")
    void eligibleDelegates() throws Exception {
        when(users.findActiveUsersByRoleCodes(List.of("HR_ADMIN"))).thenReturn(List.of(hrUser, hr(4L, "hr2@lms.com")));

        mvc.perform(get("/api/v1/delegations/eligible-delegates").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].role").value("HR_ADMIN"));
    }

    @Test
    @DisplayName("managers see their own delegations, filtered by computed status")
    void listForManagerFilteredByStatus() throws Exception {
        LocalDate today = LocalDate.now();
        when(delegations.findByDelegatorId(eq(2L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                delegation(1, today.minusDays(1), today.plusDays(1), true),   // ACTIVE
                delegation(2, today.plusDays(5), today.plusDays(9), true),    // UPCOMING
                delegation(3, today.minusDays(9), today.minusDays(5), true),  // PAST
                delegation(4, today.minusDays(1), today.plusDays(1), false)   // REVOKED
        )));

        mvc.perform(get("/api/v1/delegations").param("status", "ACTIVE").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].delegationId").value(1))
                .andExpect(jsonPath("$.data[0].scope.allTypes").value(true));
        mvc.perform(get("/api/v1/delegations").param("status", "REVOKED").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data[0].delegationId").value(4));
        mvc.perform(get("/api/v1/delegations").param("status", "UPCOMING").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data[0].delegationId").value(2));
        mvc.perform(get("/api/v1/delegations").param("status", "PAST").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(jsonPath("$.data[0].delegationId").value(3));
    }

    @Test
    void hrSeesAllDelegations() throws Exception {
        when(delegations.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                delegation(1, LocalDate.now(), LocalDate.now(), true))));

        mvc.perform(get("/api/v1/delegations").principal(auth("hr@lms.com", "HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(delegations, never()).findByDelegatorId(anyLong(), any());
    }

    @Test
    void create() throws Exception {
        LocalDate start = LocalDate.of(2030, 1, 7);
        LocalDate end = LocalDate.of(2030, 1, 11);
        when(delegations.findOverlappingDelegations(2L, start, end)).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/delegations").principal(auth("mgr@lms.com", "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delegateId", 3, "startDate", "2030-01-07", "endDate", "2030-01-11"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delegationId").value(50))
                .andExpect(jsonPath("$.delegator.id").value(2))
                .andExpect(jsonPath("$.delegate.id").value(3))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void overlappingDelegationIs409() throws Exception {
        when(delegations.findOverlappingDelegations(eq(2L), any(), any()))
                .thenReturn(Optional.of(delegation(1, LocalDate.now(), LocalDate.now(), true)));

        mvc.perform(post("/api/v1/delegations").principal(auth("mgr@lms.com", "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delegateId", 3, "startDate", "2030-01-07", "endDate", "2030-01-11"))))
                .andExpect(status().isConflict());
        verify(delegations, never()).save(any());
    }

    @Test
    void createWithUnknownDelegateIs404() throws Exception {
        mvc.perform(post("/api/v1/delegations").principal(auth("mgr@lms.com", "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delegateId", 99, "startDate", "2030-01-07", "endDate", "2030-01-11"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAndUpdate() throws Exception {
        ApprovalDelegation d = delegation(1, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 5), true);
        when(delegations.findById(1)).thenReturn(Optional.of(d));

        mvc.perform(get("/api/v1/delegations/1")).andExpect(status().isOk()).andExpect(jsonPath("$.delegationId").value(1));
        mvc.perform(get("/api/v1/delegations/2")).andExpect(status().isNotFound());

        mvc.perform(patch("/api/v1/delegations/1").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delegateId", 3, "startDate", "2030-02-01", "endDate", "2030-02-03"))))
                .andExpect(status().isOk());
        assertThat(d.getStartDate()).isEqualTo(LocalDate.of(2030, 2, 1));
        assertThat(d.getEndDate()).isEqualTo(LocalDate.of(2030, 2, 3));
    }

    @Test
    void revokeEndsDelegationToday() throws Exception {
        ApprovalDelegation d = delegation(1, LocalDate.now().minusDays(2), LocalDate.now().plusDays(5), true);
        when(delegations.findById(1)).thenReturn(Optional.of(d));

        mvc.perform(post("/api/v1/delegations/1/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        assertThat(d.getEndDate()).isEqualTo(LocalDate.now());
    }
}
