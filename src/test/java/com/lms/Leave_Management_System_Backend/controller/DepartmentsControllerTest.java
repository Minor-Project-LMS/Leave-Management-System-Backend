package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.DepartmentDto;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DepartmentsControllerTest {

    private DepartmentRepository departments;
    private UserRepository users;
    private MockMvc mvc;
    private Department engineering;

    @BeforeEach
    void setUp() {
        departments = mock(DepartmentRepository.class);
        users = mock(UserRepository.class);
        mvc = mockMvc(new DepartmentsController(departments, users));
        engineering = department(1, "Engineering");
        engineering.setDepartmentHead(manager(2L, "head@lms.com"));
        when(departments.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            if (d.getId() == null) d.setId(5);
            return d;
        });
    }

    @Test
    void listIsPaginatedOneBased() throws Exception {
        when(departments.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(engineering), PageRequest.of(1, 1), 3));
        when(users.findByDepartmentId(1)).thenReturn(List.of(employee(1L, "a@lms.com"), employee(3L, "b@lms.com")));

        mvc.perform(get("/api/v1/departments").param("page", "2").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].departmentName").value("Engineering"))
                .andExpect(jsonPath("$.data[0].departmentHeadName").value("Manager 2"))
                .andExpect(jsonPath("$.data[0].memberCount").value(2))
                .andExpect(jsonPath("$.page.page").value(2))
                .andExpect(jsonPath("$.page.totalCount").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
        verify(departments).findAll(PageRequest.of(1, 1));
    }

    @Test
    void getOne() throws Exception {
        when(departments.findById(1)).thenReturn(Optional.of(engineering));
        mvc.perform(get("/api/v1/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.departmentHeadId").value(2));
    }

    @Test
    void getMissingIs404() throws Exception {
        mvc.perform(get("/api/v1/departments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void createWithHead() throws Exception {
        when(users.findById(2L)).thenReturn(Optional.of(manager(2L, "head@lms.com")));
        DepartmentDto body = new DepartmentDto();
        body.setDepartmentName("Finance");
        body.setDepartmentHeadId(2L);

        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.departmentName").value("Finance"))
                .andExpect(jsonPath("$.departmentHeadId").value(2));
    }

    @Test
    void createWithUnknownHeadIs404() throws Exception {
        DepartmentDto body = new DepartmentDto();
        body.setDepartmentName("Finance");
        body.setDepartmentHeadId(77L);

        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
        verify(departments, never()).save(any());
    }

    @Test
    void updateRenames() throws Exception {
        when(departments.findById(1)).thenReturn(Optional.of(engineering));
        DepartmentDto body = new DepartmentDto();
        body.setDepartmentName("Platform Engineering");

        mvc.perform(patch("/api/v1/departments/1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Platform Engineering"))
                .andExpect(jsonPath("$.departmentHeadName").value("Manager 2"));
    }

    @Test
    void deleteEmptyDepartment() throws Exception {
        when(departments.findById(1)).thenReturn(Optional.of(engineering));
        when(users.findByDepartmentId(1)).thenReturn(List.of());

        mvc.perform(delete("/api/v1/departments/1")).andExpect(status().isNoContent());
        verify(departments).delete(engineering);
    }

    @Test
    void cannotDeleteDepartmentWithMembers() throws Exception {
        when(departments.findById(1)).thenReturn(Optional.of(engineering));
        when(users.findByDepartmentId(1)).thenReturn(List.of(employee(1L, "a@lms.com")));

        mvc.perform(delete("/api/v1/departments/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Department still has member(s) and cannot be deleted"));
        verify(departments, never()).delete(any(Department.class));
    }
}
