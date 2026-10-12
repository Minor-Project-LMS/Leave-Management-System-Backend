package com.lms.Leave_Management_System_Backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lms.Leave_Management_System_Backend.dto.LeavePolicyRequest;
import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeavePolicy;
import com.lms.Leave_Management_System_Backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeavePoliciesControllerTest {

    private LeavePolicyRepository policies;
    private LeaveCategoryRepository categories;
    private DepartmentRepository departments;
    private AuditTrailRepository audit;
    private UserRepository users;
    private MockMvc mvc;
    private LeaveCategory casual;
    private LeavePolicy policy;

    @BeforeEach
    void setUp() {
        policies = mock(LeavePolicyRepository.class);
        categories = mock(LeaveCategoryRepository.class);
        departments = mock(DepartmentRepository.class);
        audit = mock(AuditTrailRepository.class);
        users = mock(UserRepository.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = mockMvc(new LeavePoliciesController(policies, categories, departments, audit, users, mapper));

        casual = category(1, "Casual Leave");
        policy = new LeavePolicy();
        policy.setId(3);
        policy.setPolicyName("Casual 2030");
        policy.setCategory(casual);
        policy.setAnnualQuota(new BigDecimal("12.00"));
        policy.setMaxConsecutiveDays(3);
        policy.setEffectiveFrom(LocalDate.of(2030, 1, 1));

        when(categories.findById(1)).thenReturn(Optional.of(casual));
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hr(9L, "hr@lms.com")));
        when(policies.save(any(LeavePolicy.class))).thenAnswer(inv -> {
            LeavePolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId(20);
            return p;
        });
    }

    @Test
    void listWithAndWithoutFilters() throws Exception {
        when(policies.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(policy)));
        when(policies.findWithFilters(eq(1), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(policy)));

        mvc.perform(get("/api/v1/leave-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].policyId").value(3))
                .andExpect(jsonPath("$.data[0].category.categoryName").value("Casual Leave"));
        mvc.perform(get("/api/v1/leave-policies").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(policies).findWithFilters(eq(1), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("create defaults status to DRAFT and records a CREATE history entry")
    void create() throws Exception {
        LeavePolicyRequest body = new LeavePolicyRequest();
        body.setPolicyName("Casual 2031");
        body.setCategoryId(1);
        body.setAnnualQuota(10.0);
        body.setEffectiveFrom(LocalDate.of(2031, 1, 1));

        mvc.perform(post("/api/v1/leave-policies").principal(auth("hr@lms.com", "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyId").value(20))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.annualQuota").value(10.0))
                .andExpect(jsonPath("$.maxCarryForward").value(0.0));

        ArgumentCaptor<AuditTrail> entry = ArgumentCaptor.forClass(AuditTrail.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo(AuditTrail.AuditAction.CREATE);
        assertThat(entry.getValue().getEntityType()).isEqualTo("LEAVE_POLICY");
        assertThat(entry.getValue().getEntityId()).isEqualTo(20L);
        assertThat(entry.getValue().getPerformedBy().getId()).isEqualTo(9L);
    }

    @Test
    void createWithUnknownCategoryOrDepartmentIs404() throws Exception {
        LeavePolicyRequest body = new LeavePolicyRequest();
        body.setCategoryId(99);
        mvc.perform(post("/api/v1/leave-policies").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());

        body.setCategoryId(1);
        body.setDepartmentId(77);
        mvc.perform(post("/api/v1/leave-policies").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
        verify(policies, never()).save(any());
    }

    @Test
    void getOne() throws Exception {
        policy.setDepartment(department(4, "Sales"));
        policy.getDepartment().setDepartmentHead(manager(2L, "m@lms.com"));
        when(policies.findById(3)).thenReturn(Optional.of(policy));

        mvc.perform(get("/api/v1/leave-policies/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.departmentName").value("Sales"))
                .andExpect(jsonPath("$.department.departmentHeadName").value("Manager 2"));
        mvc.perform(get("/api/v1/leave-policies/4")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update records only the fields that actually changed")
    void updateRecordsDiff() throws Exception {
        when(policies.findById(3)).thenReturn(Optional.of(policy));
        LeavePolicyRequest body = new LeavePolicyRequest();
        body.setMaxConsecutiveDays(5);
        body.setStatus("ACTIVE");

        mvc.perform(patch("/api/v1/leave-policies/3").principal(auth("hr@lms.com", "HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxConsecutiveDays").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ArgumentCaptor<AuditTrail> entry = ArgumentCaptor.forClass(AuditTrail.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo(AuditTrail.AuditAction.UPDATE);
        assertThat(entry.getValue().getBeforeState()).contains("\"Max Consecutive Days\":3");
        assertThat(entry.getValue().getAfterState()).contains("\"Max Consecutive Days\":5");
    }

    @Test
    @DisplayName("saving with no real changes (12.00 vs 12.0) does not write history")
    void noOpUpdateWritesNoHistory() throws Exception {
        when(policies.findById(3)).thenReturn(Optional.of(policy));
        LeavePolicyRequest body = new LeavePolicyRequest();
        body.setAnnualQuota(12.0);

        mvc.perform(patch("/api/v1/leave-policies/3").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());

        verify(audit, never()).save(any());
    }

    @Test
    void historyIsBuiltFromAuditEntries() throws Exception {
        when(policies.findById(3)).thenReturn(Optional.of(policy));
        AuditTrail created = new AuditTrail();
        created.setId(1L);
        created.setAction(AuditTrail.AuditAction.CREATE);
        created.setPerformedAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        AuditTrail updated = new AuditTrail();
        updated.setId(2L);
        updated.setAction(AuditTrail.AuditAction.UPDATE);
        updated.setPerformedBy(hr(9L, "hr@lms.com"));
        updated.setBeforeState("{\"Annual Quota\":12.00,\"Status\":\"DRAFT\",\"Policy Name\":\"A\"}");
        updated.setAfterState("{\"Annual Quota\":12,\"Status\":\"ACTIVE\",\"Policy Name\":\"A\"}");
        AuditTrail corrupt = new AuditTrail();
        corrupt.setId(3L);
        corrupt.setAction(AuditTrail.AuditAction.UPDATE);
        corrupt.setBeforeState("not json");
        when(audit.findByEntityTypeAndEntityIdOrderByPerformedAtDesc("LEAVE_POLICY", 3L))
                .thenReturn(List.of(updated, created, corrupt));

        mvc.perform(get("/api/v1/leave-policies/3/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("UPDATED"))
                .andExpect(jsonPath("$[0].summary").value("Updated Status."))
                .andExpect(jsonPath("$[0].changedFields[0]").value("Status"))
                .andExpect(jsonPath("$[0].performedByName").value("HR 9"))
                .andExpect(jsonPath("$[1].action").value("CREATED"))
                .andExpect(jsonPath("$[1].summary").value("Policy created."))
                .andExpect(jsonPath("$[1].performedByName").value("System"))
                .andExpect(jsonPath("$[2].summary").value("Policy updated."));
    }

    @Test
    void historyForMissingPolicyIs404() throws Exception {
        mvc.perform(get("/api/v1/leave-policies/99/history")).andExpect(status().isNotFound());
    }
}
