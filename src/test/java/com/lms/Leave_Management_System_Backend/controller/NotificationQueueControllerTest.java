package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.mockMvc;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationQueueControllerTest {

    private NotificationQueueRepository repository;
    private MockMvc mvc;
    private NotificationQueue failed;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationQueueRepository.class);
        NotificationQueueController controller = new NotificationQueueController();
        ReflectionTestUtils.setField(controller, "notificationQueueRepository", repository);
        mvc = mockMvc(controller);

        failed = notification(1L, employee(1L, "emp@lms.com"), NotificationQueue.Channel.EMAIL, "LEAVE_APPROVED", "{}");
        failed.setStatus(NotificationQueue.NotificationStatus.FAILED);
        failed.setSentAt(LocalDateTime.now());
        failed.setRetryCount(3);
    }

    @Test
    void listWithoutFilters() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(failed)));

        mvc.perform(get("/api/v1/notification-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].recipientName").value("Employee 1"))
                .andExpect(jsonPath("$.data[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data[0].retryCount").value(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listWithFiltersUsesSpecification() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(failed)));

        mvc.perform(get("/api/v1/notification-queue").param("status", "FAILED").param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void retryOne() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(failed));

        mvc.perform(post("/api/v1/notification-queue/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        assertThat(failed.getSentAt()).isNull();
        verify(repository).save(failed);
    }

    @Test
    void retryAllFailed() throws Exception {
        when(repository.findByStatus(NotificationQueue.NotificationStatus.FAILED)).thenReturn(List.of(failed));

        mvc.perform(post("/api/v1/notification-queue/retry-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(1));
        assertThat(failed.getStatus()).isEqualTo(NotificationQueue.NotificationStatus.QUEUED);
        assertThat(failed.getRetryCount()).isEqualTo(4);
    }

    @Test
    void cancel() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(failed));
        when(repository.saveAndFlush(failed)).thenReturn(failed);

        mvc.perform(post("/api/v1/notification-queue/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void missingNotificationIs500() throws Exception {
        mvc.perform(post("/api/v1/notification-queue/99/retry")).andExpect(status().isInternalServerError());
    }

    @Test
    void exportIsStubbed() throws Exception {
        mvc.perform(get("/api/v1/notification-queue/export")).andExpect(status().isOk());
    }
}
