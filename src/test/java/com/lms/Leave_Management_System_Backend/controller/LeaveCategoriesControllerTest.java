package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.LeaveCategoryRequest;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeaveCategoriesControllerTest {

    private LeaveCategoryRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(LeaveCategoryRepository.class);
        mvc = mockMvc(new LeaveCategoriesController(repository));
        when(repository.save(any(LeaveCategory.class))).thenAnswer(inv -> {
            LeaveCategory c = inv.getArgument(0);
            if (c.getId() == null) c.setId(10);
            return c;
        });
    }

    @Test
    @DisplayName("status filter matches the category's status column, not its id")
    void filterByStatus() throws Exception {
        LeaveCategory active = category(1, "Casual Leave");
        LeaveCategory inactive = category(7, "Old Leave");
        inactive.setStatus("INACTIVE");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(active, inactive)));

        mvc.perform(get("/api/v1/leave-categories").param("status", "inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].categoryName").value("Old Leave"))
                .andExpect(jsonPath("$.data[0].isSystemCategory").value(false));

        mvc.perform(get("/api/v1/leave-categories").param("status", "ALL"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].isSystemCategory").value(true));
    }

    @Test
    void getOneAndMissing() throws Exception {
        when(repository.findById(1)).thenReturn(Optional.of(category(1, "Casual Leave")));
        mvc.perform(get("/api/v1/leave-categories/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/v1/leave-categories/2")).andExpect(status().isNotFound());
    }

    @Test
    void createAppliesDefaults() throws Exception {
        LeaveCategoryRequest body = new LeaveCategoryRequest();
        body.setName("  Wellness Leave ");

        mvc.perform(post("/api/v1/leave-categories").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.categoryName").value("Wellness Leave"));

        ArgumentCaptor<LeaveCategory> saved = ArgumentCaptor.forClass(LeaveCategory.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isPaid()).isTrue();
        assertThat(saved.getValue().isRequiresDocument()).isFalse();
        assertThat(saved.getValue().getDefaultAnnualQuota()).isZero();
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createRequiresName() throws Exception {
        mvc.perform(post("/api/v1/leave-categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Leave type name is required."));
    }

    @Test
    void createDuplicateIs409() throws Exception {
        when(repository.existsByCategoryNameIgnoreCase("Casual Leave")).thenReturn(true);
        mvc.perform(post("/api/v1/leave-categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Casual Leave\"}"))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void updateChangesProvidedFields() throws Exception {
        LeaveCategory existing = category(8, "Study Leave");
        when(repository.findById(8)).thenReturn(Optional.of(existing));
        LeaveCategoryRequest body = new LeaveCategoryRequest();
        body.setName("Exam Leave");
        body.setPaid(false);
        body.setDefaultAnnualQuota(5.0);
        body.setStatus("INACTIVE");

        mvc.perform(patch("/api/v1/leave-categories/8").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Exam Leave"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.defaultAnnualQuota").value(5.0));
        assertThat(existing.isPaid()).isFalse();
    }

    @Test
    void customCategoryCanBeDeleted() throws Exception {
        LeaveCategory custom = category(8, "Study Leave");
        when(repository.findById(8)).thenReturn(Optional.of(custom));
        mvc.perform(delete("/api/v1/leave-categories/8")).andExpect(status().isNoContent());
        verify(repository).delete(custom);
    }

    @Test
    @DisplayName("system categories (id <= 5) cannot be deleted")
    void systemCategoryProtected() throws Exception {
        when(repository.findById(3)).thenReturn(Optional.of(category(3, "Sick Leave")));
        mvc.perform(delete("/api/v1/leave-categories/3")).andExpect(status().isConflict());
        verify(repository, never()).delete(any(LeaveCategory.class));
    }
}
