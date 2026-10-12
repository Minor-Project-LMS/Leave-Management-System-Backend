package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.EmployeeInput;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.RoleRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
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

class EmployeesControllerTest {

    private UserRepository users;
    private AttachmentService attachments;
    private DepartmentRepository departments;
    private RoleRepository roles;
    private PasswordEncoder encoder;
    private LeaveLedgerProvisioningService provisioning;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        attachments = mock(AttachmentService.class);
        departments = mock(DepartmentRepository.class);
        roles = mock(RoleRepository.class);
        encoder = mock(PasswordEncoder.class);
        provisioning = mock(LeaveLedgerProvisioningService.class);
        mvc = mockMvc(new EmployeesController(users, attachments, departments, roles, encoder, provisioning));

        when(encoder.encode(anyString())).thenReturn("hashed");
        when(roles.findByRoleCode(anyString())).thenAnswer(inv -> Optional.of(role(inv.getArgument(0))));
        when(departments.findById(10)).thenReturn(Optional.of(department(10, "Engineering")));
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(100L);
            return u;
        });
    }

    private EmployeeInput createInput() {
        EmployeeInput in = new EmployeeInput();
        in.setFullName("New Joiner");
        in.setEmail("new@lms.com");
        in.setRole("EMPLOYEE");
        in.setDepartmentId(10);
        in.setDateOfJoining(LocalDate.of(2030, 1, 1));
        return in;
    }

    @Test
    void listDirectory() throws Exception {
        User u = employee(1L, "a@lms.com");
        u.setReportsTo(manager(2L, "m@lms.com"));
        u.setDepartment(department(10, "Engineering"));
        when(users.findEmployeeDirectory(isNull(), isNull(), eq(User.EmploymentStatus.ACTIVE), eq("a"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u)));

        mvc.perform(get("/api/v1/employees").param("status", "ACTIVE").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reportsToName").value("Manager 2"))
                .andExpect(jsonPath("$.data[0].departmentName").value("Engineering"));
    }

    @Nested
    class Create {

        @Test
        @DisplayName("generates the next EMP code, sets default password and ACTIVE status")
        void createGeneratesCode() throws Exception {
            User seven = user(1L, "a", "a@x", "EMPLOYEE");
            seven.setEmployeeCode("EMP007");
            User legacy = user(12L, "b", "b@x", "EMPLOYEE");
            legacy.setEmployeeCode("EMP-1699999999"); // legacy format is ignored by the sequence
            when(users.findByEmployeeCodeStartingWith("EMP")).thenReturn(List.of(seven, legacy));

            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content(json(createInput())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.employeeCode").value("EMP008"))
                    .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.departmentName").value("Engineering"));
            verify(encoder).encode("Welcome@123");
        }

        @Test
        void managerGetsMgrPrefix() throws Exception {
            EmployeeInput in = createInput();
            in.setRole("MANAGER");
            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content(json(in)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.employeeCode").value("MGR001"));
        }

        @Test
        void explicitCodeIsKept() throws Exception {
            EmployeeInput in = createInput();
            in.setEmployeeCode("CUSTOM9");
            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content(json(in)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.employeeCode").value("CUSTOM9"));
        }

        @Test
        void duplicateEmailIs422() throws Exception {
            when(users.findByEmailIgnoreCase("new@lms.com")).thenReturn(Optional.of(employee(5L, "new@lms.com")));
            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content(json(createInput())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value("Email already in use"));
        }

        @Test
        void duplicateCodeIs422() throws Exception {
            EmployeeInput in = createInput();
            in.setEmployeeCode("EMP001");
            when(users.findByEmployeeCode("EMP001")).thenReturn(Optional.of(employee(1L, "x@lms.com")));
            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content(json(in)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("create requires name, email, role, department and joining date")
        void missingRequiredFieldsIs400() throws Exception {
            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
            verify(users, never()).save(any());
        }

        @Test
        void unknownManagerIs404() throws Exception {
            EmployeeInput in = createInput();
            in.setReportsTo(77);
            mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content(json(in)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Update {

        private User existing;

        @BeforeEach
        void setUp() {
            existing = employee(1L, "a@lms.com");
            existing.setEmployeeCode("EMP001");
            when(users.findWithReportsToById(1L)).thenReturn(Optional.of(existing));
        }

        @Test
        @DisplayName("partial update with only a role is valid; promotion to MANAGER regenerates the code")
        void promotionRegeneratesCode() throws Exception {
            when(users.findByEmployeeCodeStartingWith("MGR")).thenReturn(List.of());

            mvc.perform(patch("/api/v1/employees/1").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("role", "MANAGER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("MANAGER"))
                    .andExpect(jsonPath("$.employeeCode").value("MGR001"));
        }

        @Test
        void sameRoleKeepsCode() throws Exception {
            mvc.perform(patch("/api/v1/employees/1").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("role", "EMPLOYEE", "designation", "Senior Engineer"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.employeeCode").value("EMP001"))
                    .andExpect(jsonPath("$.designation").value("Senior Engineer"));
        }

        @Test
        void emailTakenByAnotherUserIs422() throws Exception {
            when(users.findByEmailIgnoreCase("b@lms.com")).thenReturn(Optional.of(employee(2L, "b@lms.com")));
            mvc.perform(patch("/api/v1/employees/1").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "b@lms.com"))))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void keepingOwnEmailIsFine() throws Exception {
            when(users.findByEmailIgnoreCase("a@lms.com")).thenReturn(Optional.of(existing));
            mvc.perform(patch("/api/v1/employees/1").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "a@lms.com", "employmentStatus", "ON_LEAVE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.employmentStatus").value("ON_LEAVE"));
        }

        @Test
        void invalidEmailFormatIs400() throws Exception {
            mvc.perform(patch("/api/v1/employees/1").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "not-an-email"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void softDeleteMarksSeparated() throws Exception {
            mvc.perform(delete("/api/v1/employees/1")).andExpect(status().isNoContent());
            assertThat(existing.getEmploymentStatus()).isEqualTo(User.EmploymentStatus.SEPARATED);
            verify(users).save(existing);
        }

        @Test
        void missingEmployeeIs404() throws Exception {
            mvc.perform(get("/api/v1/employees/99")).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/employees/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        }
    }

    @Nested
    class Ledger {

        private User report;
        private User mgr;

        @BeforeEach
        void setUp() {
            mgr = manager(2L, "mgr@lms.com");
            report = employee(1L, "a@lms.com");
            report.setReportsTo(mgr);
            when(users.findWithReportsToById(1L)).thenReturn(Optional.of(report));
            when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(mgr));
            when(users.findByEmailIgnoreCase("other@lms.com")).thenReturn(Optional.of(manager(3L, "other@lms.com")));
            when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hr(4L, "hr@lms.com")));
            LeaveLedger row = ledger(report, category(1, "Casual Leave"), 2030, "7");
            when(provisioning.getOrInitializeLedger(report, 2030)).thenReturn(List.of(row));
        }

        @Test
        void directManagerCanView() throws Exception {
            mvc.perform(get("/api/v1/employees/1/leave-ledger").param("year", "2030").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].closingBalance").value(7.0));
        }

        @Test
        void otherManagerIsBlocked() throws Exception {
            mvc.perform(get("/api/v1/employees/1/leave-ledger").param("year", "2030").principal(auth("other@lms.com", "MANAGER")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void hrCanView() throws Exception {
            mvc.perform(get("/api/v1/employees/1/leave-ledger").param("year", "2030").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isOk());
        }

        @Test
        void assignQuotaEchoesValues() throws Exception {
            mvc.perform(post("/api/v1/employees/1/quota").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("categoryId", 1, "fiscalYear", 2030, "quota", 15.0))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openingBalance").value(15.0))
                    .andExpect(jsonPath("$.availableBalance").value(15.0));
        }

        @Test
        void assignQuotaValidatesBody() throws Exception {
            mvc.perform(post("/api/v1/employees/1/quota").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void importAndExport() throws Exception {
        mvc.perform(multipart("/api/v1/employees/import").file(new MockMultipartFile("file", "e.csv", "text/csv", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0));
        mvc.perform(multipart("/api/v1/employees/import")
                        .file(new MockMultipartFile("file", "e.csv", "text/csv", new byte[10 * 1024 * 1024 + 1])))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/employees/export")).andExpect(status().isOk());
    }
}
