package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.HolidayDto;
import com.lms.Leave_Management_System_Backend.model.Holiday;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.department;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HolidaysControllerTest {

    private HolidayRepository holidays;
    private DepartmentRepository departments;
    private MockMvc mvc;
    private Holiday diwali;

    @BeforeEach
    void setUp() {
        holidays = mock(HolidayRepository.class);
        departments = mock(DepartmentRepository.class);
        mvc = mockMvc(new HolidaysController(holidays, departments));
        diwali = new Holiday();
        diwali.setId(1);
        diwali.setHolidayName("Diwali");
        diwali.setHolidayDate(LocalDate.of(2030, 10, 26));
        when(holidays.save(any(Holiday.class))).thenAnswer(inv -> {
            Holiday h = inv.getArgument(0);
            if (h.getId() == null) h.setId(9);
            return h;
        });
    }

    @Test
    void listByYearAndMonthQueriesThatMonth() throws Exception {
        when(holidays.findByHolidayDateBetween(eq(LocalDate.of(2030, 2, 1)), eq(LocalDate.of(2030, 2, 28)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(diwali)));

        mvc.perform(get("/api/v1/holidays").param("year", "2030").param("month", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Diwali"));
    }

    @Test
    void listByYearQueriesWholeYear() throws Exception {
        when(holidays.findByHolidayDateBetween(eq(LocalDate.of(2030, 1, 1)), eq(LocalDate.of(2030, 12, 31)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(diwali)));

        mvc.perform(get("/api/v1/holidays").param("year", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void listWithoutFiltersReturnsAll() throws Exception {
        when(holidays.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(diwali)));
        mvc.perform(get("/api/v1/holidays")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void upcomingDefaultsToNext30Days() throws Exception {
        LocalDate today = LocalDate.now();
        when(holidays.findByHolidayDateBetween(today, today.plusDays(30))).thenReturn(List.of(diwali));

        mvc.perform(get("/api/v1/holidays/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Diwali"));
    }

    @Test
    void upcomingForFutureMonth() throws Exception {
        int year = LocalDate.now().getYear() + 2;
        when(holidays.findByHolidayDateBetween(LocalDate.of(year, 3, 1), LocalDate.of(year, 3, 31))).thenReturn(List.of(diwali));

        mvc.perform(get("/api/v1/holidays/upcoming").param("month", "3").param("year", String.valueOf(year)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getOneAndMissing() throws Exception {
        diwali.setDepartment(department(4, null));
        when(holidays.findById(1)).thenReturn(Optional.of(diwali));

        mvc.perform(get("/api/v1/holidays/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(4))
                .andExpect(jsonPath("$.departmentName").value("Unknown"));
        mvc.perform(get("/api/v1/holidays/2")).andExpect(status().isNotFound());
    }

    @Test
    void createDepartmentSpecificHoliday() throws Exception {
        when(departments.findById(4)).thenReturn(Optional.of(department(4, "Sales")));
        HolidayDto body = new HolidayDto();
        body.setName("Founders Day");
        body.setDate(LocalDate.of(2030, 4, 1));
        body.setDepartmentId(4);
        body.setRestricted(true);

        mvc.perform(post("/api/v1/holidays").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.restricted").value(true))
                .andExpect(jsonPath("$.departmentName").value("Sales"));
    }

    @Test
    void createWithUnknownDepartmentIs404() throws Exception {
        HolidayDto body = new HolidayDto();
        body.setName("X");
        body.setDepartmentId(99);
        mvc.perform(post("/api/v1/holidays").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateOnlyChangesProvidedFields() throws Exception {
        when(holidays.findById(1)).thenReturn(Optional.of(diwali));
        HolidayDto body = new HolidayDto();
        body.setDate(LocalDate.of(2030, 10, 27));

        mvc.perform(patch("/api/v1/holidays/1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Diwali"));

        ArgumentCaptor<Holiday> saved = ArgumentCaptor.forClass(Holiday.class);
        verify(holidays).save(saved.capture());
        assertThat(saved.getValue().getHolidayDate()).isEqualTo(LocalDate.of(2030, 10, 27));
    }

    @Test
    void deleteHoliday() throws Exception {
        when(holidays.findById(1)).thenReturn(Optional.of(diwali));
        mvc.perform(delete("/api/v1/holidays/1")).andExpect(status().isNoContent());
        verify(holidays).delete(diwali);
    }

    @Test
    void importAcceptsSmallFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "holidays.csv", "text/csv", "name,date".getBytes());
        mvc.perform(multipart("/api/v1/holidays/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0));
    }

    @Test
    void importRejectsFilesOver10Mb() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", new byte[10 * 1024 * 1024 + 1]);
        mvc.perform(multipart("/api/v1/holidays/import").file(file))
                .andExpect(status().isConflict());
    }
}
