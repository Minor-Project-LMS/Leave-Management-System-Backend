package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.ReportsAggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LeaveCategoryRepository leaveCategoryRepository;

    @Autowired
    private LeaveLedgerRepository leaveLedgerRepository;

    // The numbers on the HR Reports & Analytics page (and the department
    // panels on the HR Dashboard and Employee Management pages) used to be
    // hardcoded sample values here. They're now calculated from the real
    // leave requests, users, categories and ledgers — the definitions live
    // in ReportsAggregator, which is where they're tested.
    //
    // These are HR-only. Summary and leave-trend used to allow MANAGER too,
    // but they return org-wide figures now, and only HR pages call them.
    private ReportsAggregator aggregator() {
        return new ReportsAggregator(
                leaveRequestRepository.findAll(),
                userRepository.findAll(),
                departmentRepository.findAll(),
                leaveCategoryRepository.findAll(),
                leaveLedgerRepository.findAll(),
                LocalDate.now());
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be a date in YYYY-MM-DD format.");
        }
    }

    @GetMapping("/summary")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<ReportSummary> getReportSummary(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer departmentId,
            Authentication authentication) {

        return ResponseEntity.ok(aggregator().summary(
                parseDate(dateFrom, "dateFrom"), parseDate(dateTo, "dateTo"), departmentId));
    }

    @GetMapping("/leave-trend")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<List<LeaveTrendPoint>> getLeaveTrend(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer departmentId,
            Authentication authentication) {

        int resolvedYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(aggregator().monthlyTrend(resolvedYear, departmentId));
    }

    // Per-leave-type monthly lines for the "Leave Trend Overview" chart,
    // which used to be fed mock data on the frontend.
    @GetMapping("/leave-trend-by-type")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<List<LeaveTrendSeries>> getLeaveTrendByType(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer departmentId,
            Authentication authentication) {

        int resolvedYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(aggregator().trendByType(resolvedYear, departmentId));
    }

    // Org-wide leave-by-type breakdown for the distribution donut, which
    // also used to be mock-only on the frontend.
    @GetMapping("/distribution")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<ReportDistributionResponse> getDistribution(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer departmentId,
            Authentication authentication) {

        return ResponseEntity.ok(aggregator().distribution(
                parseDate(dateFrom, "dateFrom"), parseDate(dateTo, "dateTo"), departmentId));
    }

    @GetMapping("/department-summary")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<List<DepartmentSummary>> getDepartmentSummary(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication) {

        return ResponseEntity.ok(aggregator().departmentSummary(
                parseDate(dateFrom, "dateFrom"), parseDate(dateTo, "dateTo")));
    }

    @GetMapping("/top-employees")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<List<TopEmployee>> getTopEmployees(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {

        return ResponseEntity.ok(aggregator().topEmployees(
                parseDate(dateFrom, "dateFrom"), parseDate(dateTo, "dateTo"), limit, departmentId));
    }

    @PostMapping("/export")
    @RequireRole({"HR_ADMIN"})
    @Transactional(readOnly = true)
    public ResponseEntity<ReportExportJob> createExportJob(
            @RequestBody ReportExportRequest request,
            Authentication authentication) {

        // This used to just fabricate a fake job id and always claim
        // "READY" a moment later — GET /reports/export/{jobId} pointed at
        // a file that was never actually written anywhere. Report
        // generation here is fast enough (in-memory CSV over data already
        // in the database) that there's no real need for an async
        // job/poll dance — this now builds the file synchronously and
        // returns it immediately as a base64 data URL the frontend can
        // hand straight to a download link, while keeping the same
        // request/response shape so nothing else has to change.
        String csv = buildReportCsv(request.getReportType(), request.getDepartmentId());
        String filename = reportFilename(request.getReportType());

        String downloadUrl = "data:text/csv;charset=utf-8;base64," +
                java.util.Base64.getEncoder().encodeToString(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ReportExportJob job = new ReportExportJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setStatus("READY");
        job.setDownloadUrl(downloadUrl);
        job.setFilename(filename);

        return ResponseEntity.status(202).body(job);
    }

    @GetMapping("/export/{jobId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<ReportExportJob> getExportJobStatus(
            @PathVariable String jobId,
            Authentication authentication) {

        // Kept only so any existing caller polling this URL doesn't 404 —
        // createExportJob() above now always returns the finished file
        // immediately, so this path shouldn't normally be hit anymore.
        ReportExportJob job = new ReportExportJob();
        job.setJobId(jobId);
        job.setStatus("READY");
        job.setDownloadUrl(null);

        return ResponseEntity.ok(job);
    }

    // --- Real report generation (CSV) ---
    // reportType "ALL" bundles every report below into one file, with a
    // section header separating each — this is what the "Export Report"
    // button (as opposed to an individual Quick Reports item) uses.
    private String buildReportCsv(String reportType, Integer departmentId) {
        String type = reportType == null ? "ALL" : reportType.toUpperCase();

        if ("ALL".equals(type) || "COMBINED".equals(type)) {
            StringBuilder combined = new StringBuilder();
            combined.append(buildLeaveSummaryCsv(departmentId)).append("\n\n");
            combined.append(buildDepartmentWiseCsv()).append("\n\n");
            combined.append(buildEmployeeLeaveCsv(departmentId)).append("\n\n");
            combined.append(buildLeaveTrendCsv(departmentId)).append("\n\n");
            combined.append(buildPendingRequestsCsv(departmentId));
            return combined.toString();
        }

        switch (type) {
            case "LEAVE_SUMMARY":
                return buildLeaveSummaryCsv(departmentId);
            case "DEPARTMENT_WISE":
                return buildDepartmentWiseCsv();
            case "EMPLOYEE_LEAVE":
                return buildEmployeeLeaveCsv(departmentId);
            case "LEAVE_TREND":
                return buildLeaveTrendCsv(departmentId);
            case "PENDING_REQUESTS":
                return buildPendingRequestsCsv(departmentId);
            default:
                throw new IllegalArgumentException("Unknown report type: " + reportType);
        }
    }

    private String reportFilename(String reportType) {
        String type = reportType == null ? "ALL" : reportType.toUpperCase();
        String today = LocalDate.now().toString();
        if ("ALL".equals(type) || "COMBINED".equals(type)) {
            return "hr-reports-combined-" + today + ".csv";
        }
        return type.toLowerCase().replace('_', '-') + "-report-" + today + ".csv";
    }

    private String csvField(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String csvRow(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            row.append(csvField(values[i]));
        }
        return row.toString();
    }

    private List<LeaveRequest> allRequestsFiltered(Integer departmentId) {
        List<LeaveRequest> all = leaveRequestRepository.findAll();
        if (departmentId == null) return all;
        return all.stream()
                .filter(r -> r.getUser().getDepartment() != null
                        && departmentId.equals(r.getUser().getDepartment().getId()))
                .collect(Collectors.toList());
    }

    private String buildLeaveSummaryCsv(Integer departmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LEAVE SUMMARY REPORT ===\n");
        sb.append(csvRow("Employee", "Employee Code", "Department", "Leave Type", "Start Date", "End Date", "Total Days", "LOP Days", "Status")).append('\n');

        for (LeaveRequest r : allRequestsFiltered(departmentId)) {
            User u = r.getUser();
            sb.append(csvRow(
                    u.getFullName(),
                    u.getEmployeeCode(),
                    u.getDepartment() != null ? u.getDepartment().getDepartmentName() : "",
                    r.getCategory().getName(),
                    r.getStartDate(),
                    r.getEndDate(),
                    r.getTotalDays(),
                    r.getLopDays(),
                    r.getStatus()
            )).append('\n');
        }
        return sb.toString();
    }

    private String buildDepartmentWiseCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DEPARTMENT WISE REPORT ===\n");
        sb.append(csvRow("Department", "Total Employees", "Total Leave Days Taken", "Avg Days Per Employee")).append('\n');

        List<Department> departments = departmentRepository.findAll();
        for (Department d : departments) {
            List<User> deptUsers = userRepository.findByDepartmentId(d.getId());
            java.math.BigDecimal totalDays = allRequestsFiltered(d.getId()).stream()
                    .filter(r -> r.getStatus() == LeaveRequest.RequestStatus.APPROVED)
                    .map(LeaveRequest::getTotalDays)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            double avg = deptUsers.isEmpty() ? 0 : totalDays.doubleValue() / deptUsers.size();

            sb.append(csvRow(
                    d.getDepartmentName(),
                    deptUsers.size(),
                    totalDays,
                    String.format(Locale.US, "%.2f", avg)
            )).append('\n');
        }
        return sb.toString();
    }

    private String buildEmployeeLeaveCsv(Integer departmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EMPLOYEE LEAVE REPORT ===\n");
        sb.append(csvRow("Employee", "Employee Code", "Department", "Approved Days Taken", "Pending Days", "LOP Days")).append('\n');

        Map<Long, List<LeaveRequest>> byUser = allRequestsFiltered(departmentId).stream()
                .collect(Collectors.groupingBy(r -> r.getUser().getId()));

        for (Map.Entry<Long, List<LeaveRequest>> entry : byUser.entrySet()) {
            List<LeaveRequest> requests = entry.getValue();
            User u = requests.get(0).getUser();

            java.math.BigDecimal approved = requests.stream()
                    .filter(r -> r.getStatus() == LeaveRequest.RequestStatus.APPROVED)
                    .map(LeaveRequest::getTotalDays)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal pending = requests.stream()
                    .filter(r -> r.getStatus() == LeaveRequest.RequestStatus.PENDING_L1 || r.getStatus() == LeaveRequest.RequestStatus.PENDING_L2)
                    .map(LeaveRequest::getTotalDays)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal lop = requests.stream()
                    .map(LeaveRequest::getLopDays)
                    .filter(java.util.Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            sb.append(csvRow(
                    u.getFullName(),
                    u.getEmployeeCode(),
                    u.getDepartment() != null ? u.getDepartment().getDepartmentName() : "",
                    approved,
                    pending,
                    lop
            )).append('\n');
        }
        return sb.toString();
    }

    private String buildLeaveTrendCsv(Integer departmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LEAVE TREND REPORT (by month) ===\n");
        sb.append(csvRow("Month", "Approved Requests", "Total Days")).append('\n');

        Map<String, java.math.BigDecimal[]> byMonth = new java.util.TreeMap<>();
        for (LeaveRequest r : allRequestsFiltered(departmentId)) {
            if (r.getStatus() != LeaveRequest.RequestStatus.APPROVED) continue;
            String monthKey = String.format("%04d-%02d", r.getStartDate().getYear(), r.getStartDate().getMonthValue());
            byMonth.computeIfAbsent(monthKey, k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
            java.math.BigDecimal[] agg = byMonth.get(monthKey);
            agg[0] = agg[0].add(java.math.BigDecimal.ONE);
            agg[1] = agg[1].add(r.getTotalDays());
        }

        for (Map.Entry<String, java.math.BigDecimal[]> entry : byMonth.entrySet()) {
            String[] parts = entry.getKey().split("-");
            String label = java.time.Month.of(Integer.parseInt(parts[1])).getDisplayName(TextStyle.SHORT, Locale.US) + " " + parts[0];
            sb.append(csvRow(label, entry.getValue()[0], entry.getValue()[1])).append('\n');
        }
        return sb.toString();
    }

    private String buildPendingRequestsCsv(Integer departmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PENDING REQUESTS REPORT ===\n");
        sb.append(csvRow("Employee", "Department", "Leave Type", "Start Date", "End Date", "Total Days", "Applied On", "Current Approver", "Status")).append('\n');

        List<LeaveRequest> pending = new ArrayList<>();
        pending.addAll(leaveRequestRepository.findByStatus(LeaveRequest.RequestStatus.PENDING_L1));
        pending.addAll(leaveRequestRepository.findByStatus(LeaveRequest.RequestStatus.PENDING_L2));
        pending.sort(Comparator.comparing(LeaveRequest::getAppliedAt));

        for (LeaveRequest r : pending) {
            User u = r.getUser();
            if (departmentId != null && (u.getDepartment() == null || !departmentId.equals(u.getDepartment().getId()))) {
                continue;
            }
            sb.append(csvRow(
                    u.getFullName(),
                    u.getDepartment() != null ? u.getDepartment().getDepartmentName() : "",
                    r.getCategory().getName(),
                    r.getStartDate(),
                    r.getEndDate(),
                    r.getTotalDays(),
                    r.getAppliedAt(),
                    r.getCurrentApprover() != null ? r.getCurrentApprover().getFullName() : "",
                    r.getStatus()
            )).append('\n');
        }
        return sb.toString();
    }
}
