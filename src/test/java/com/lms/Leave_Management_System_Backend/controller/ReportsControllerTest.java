package com.lms.Leave_Management_System_Backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportsControllerTest {

    private LeaveRequestRepository requests;
    private UserRepository users;
    private DepartmentRepository departments;
    private LeaveCategoryRepository categories;
    private LeaveLedgerRepository ledgers;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        requests = mock(LeaveRequestRepository.class);
        users = mock(UserRepository.class);
        departments = mock(DepartmentRepository.class);
        categories = mock(LeaveCategoryRepository.class);
        ledgers = mock(LeaveLedgerRepository.class);
        ReportsController controller = new ReportsController();
        ReflectionTestUtils.setField(controller, "leaveRequestRepository", requests);
        ReflectionTestUtils.setField(controller, "userRepository", users);
        ReflectionTestUtils.setField(controller, "departmentRepository", departments);
        ReflectionTestUtils.setField(controller, "leaveCategoryRepository", categories);
        ReflectionTestUtils.setField(controller, "leaveLedgerRepository", ledgers);
        mvc = mockMvc(controller);

        Department eng = department(1, "Engineering");
        User alice = employee(1L, "alice@lms.com");
        alice.setName("Alice, Jr");   // comma forces CSV quoting
        alice.setDepartment(eng);
        LeaveCategory casual = category(1, "Casual Leave");
        LeaveRequest approved = leaveRequest(1L, alice, casual, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12), "3",
                LeaveRequest.RequestStatus.APPROVED);
        LeaveRequest pending = leaveRequest(2L, alice, casual, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 1), "1",
                LeaveRequest.RequestStatus.PENDING_L1);
        pending.setCurrentApprover(manager(2L, "mgr@lms.com"));

        when(requests.findAll()).thenReturn(List.of(approved, pending));
        when(requests.findByStatus(LeaveRequest.RequestStatus.PENDING_L1)).thenReturn(List.of(pending));
        when(users.findAll()).thenReturn(List.of(alice));
        when(users.findByDepartmentId(1)).thenReturn(List.of(alice));
        when(departments.findAll()).thenReturn(List.of(eng));
        when(categories.findAll()).thenReturn(List.of(casual));
    }

    @Test
    void summaryUsesRealData() throws Exception {
        mvc.perform(get("/api/v1/reports/summary").param("dateFrom", "2030-01-01").param("dateTo", "2030-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeavesTaken").value(3.0))
                .andExpect(jsonPath("$.totalEmployees").value(1))
                .andExpect(jsonPath("$.pendingRequests").value(1));
    }

    @Test
    void badDateIs400() throws Exception {
        mvc.perform(get("/api/v1/reports/summary").param("dateFrom", "01/01/2030"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("dateFrom must be a date in YYYY-MM-DD format."));
    }

    @Test
    void invertedRangeIs400() throws Exception {
        mvc.perform(get("/api/v1/reports/distribution").param("dateFrom", "2030-12-01").param("dateTo", "2030-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chartEndpoints() throws Exception {
        mvc.perform(get("/api/v1/reports/leave-trend").param("year", "2030"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].days").value(3.0));
        mvc.perform(get("/api/v1/reports/leave-trend-by-type").param("year", "2030"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("Casual Leave"));
        mvc.perform(get("/api/v1/reports/distribution").param("dateFrom", "2030-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3.0));
        mvc.perform(get("/api/v1/reports/department-summary").param("dateFrom", "2030-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].departmentName").value("Engineering"));
        mvc.perform(get("/api/v1/reports/top-employees").param("dateFrom", "2030-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].totalDaysTaken").value(3.0));
    }

    private String decodeCsv(String responseBody) {
        String url = JsonPath.read(responseBody, "$.downloadUrl");
        assertThat(url).startsWith("data:text/csv;charset=utf-8;base64,");
        return new String(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("combined export contains every section and escapes commas")
    void combinedExport() throws Exception {
        String body = mvc.perform(post("/api/v1/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reportType", "ALL"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.filename").value("hr-reports-combined-" + LocalDate.now() + ".csv"))
                .andReturn().getResponse().getContentAsString();

        String csv = decodeCsv(body);
        assertThat(csv)
                .contains("=== LEAVE SUMMARY REPORT ===")
                .contains("=== DEPARTMENT WISE REPORT ===")
                .contains("=== EMPLOYEE LEAVE REPORT ===")
                .contains("=== LEAVE TREND REPORT (by month) ===")
                .contains("=== PENDING REQUESTS REPORT ===")
                .contains("\"Alice, Jr\"")
                .contains("Engineering,1,3,3.00")
                .contains("Jan 2030,1,3");
    }

    @Test
    void singleReportTypes() throws Exception {
        for (String type : List.of("LEAVE_SUMMARY", "DEPARTMENT_WISE", "EMPLOYEE_LEAVE", "LEAVE_TREND", "PENDING_REQUESTS")) {
            mvc.perform(post("/api/v1/reports/export").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("reportType", type.toLowerCase()))))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.filename").value(
                            type.toLowerCase().replace('_', '-') + "-report-" + LocalDate.now() + ".csv"));
        }
    }

    @Test
    void pendingReportFilteredByDepartment() throws Exception {
        String body = mvc.perform(post("/api/v1/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reportType", "PENDING_REQUESTS", "departmentId", 99))))
                .andReturn().getResponse().getContentAsString();

        assertThat(decodeCsv(body)).doesNotContain("Alice");
    }

    @Test
    void unknownReportTypeIs400() throws Exception {
        mvc.perform(post("/api/v1/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reportType", "NOPE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportStatusStub() throws Exception {
        mvc.perform(get("/api/v1/reports/export/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.status").value("READY"));
    }
}
