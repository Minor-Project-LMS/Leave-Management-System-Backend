package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import com.lms.Leave_Management_System_Backend.repository.AuditTrailRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditTrailControllerTest {

    private AuditTrailRepository repository;
    private UserRepository users;
    private MockMvc mvc;
    private AuditTrail entry;

    @BeforeEach
    void setUp() {
        repository = mock(AuditTrailRepository.class);
        users = mock(UserRepository.class);
        AuditTrailController controller = new AuditTrailController();
        ReflectionTestUtils.setField(controller, "auditTrailRepository", repository);
        ReflectionTestUtils.setField(controller, "userRepository", users);
        mvc = mockMvc(controller);

        entry = new AuditTrail();
        entry.setId(11L);
        entry.setEntityType("LEAVE_REQUEST");
        entry.setEntityId(100L);
        entry.setAction(AuditTrail.AuditAction.APPROVE);
        entry.setPerformedBy(manager(2L, "mgr@lms.com"));
        entry.setPerformedAt(LocalDateTime.of(2030, 1, 2, 10, 0));
        entry.setBeforeState("{\"status\":\"PENDING_L1\"}");
        entry.setAfterState("{\"status\":\"APPROVED\"}");
    }

    @Test
    void searchWithoutFiltersUsesPlainPaging() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));

        mvc.perform(get("/api/v1/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].action").value("APPROVE"))
                .andExpect(jsonPath("$.data[0].performedByName").value("Manager 2"))
                .andExpect(jsonPath("$.data[0].description").value("APPROVE operation on LEAVE_REQUEST"));
        verify(repository).findAll(PageRequest.of(0, 20, Sort.by("performedAt").descending()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchWithFiltersUsesSpecification() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));

        mvc.perform(get("/api/v1/audit-log")
                        .param("action", "approve")
                        .param("entityType", "LEAVE_REQUEST")
                        .param("dateFrom", "2030-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("recent activity shows only the caller's own actions")
    void recentActivityIsScopedToCaller() throws Exception {
        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager(2L, "mgr@lms.com")));
        when(repository.findByPerformedById(eq(2L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(entry)));

        mvc.perform(get("/api/v1/audit-log/recent").param("limit", "5").principal(auth("mgr@lms.com", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(repository).findByPerformedById(2L, PageRequest.of(0, 5, Sort.by("performedAt").descending()));
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void detailIncludesStates() throws Exception {
        when(repository.findById(11L)).thenReturn(Optional.of(entry));

        mvc.perform(get("/api/v1/audit-log/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value(100))
                .andExpect(jsonPath("$.beforeState.state").value("{\"status\":\"PENDING_L1\"}"))
                .andExpect(jsonPath("$.afterState.state").value("{\"status\":\"APPROVED\"}"));
    }

    @Test
    @DisplayName("BUG GUARD: a missing audit entry returns 500 instead of 404")
    void missingDetailIs500() throws Exception {
        mvc.perform(get("/api/v1/audit-log/999"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void exportIsStubbed() throws Exception {
        mvc.perform(get("/api/v1/audit-log/export")).andExpect(status().isOk());
    }
}
