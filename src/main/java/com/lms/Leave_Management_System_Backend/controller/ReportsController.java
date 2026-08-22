package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    @GetMapping("/summary")
    @RequireRole({"HR_ADMIN", "MANAGER"})
    public ResponseEntity<ReportSummary> getReportSummary(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer locationId,
            Authentication authentication) {

        // Simplified implementation - would query actual report data
        ReportSummary summary = new ReportSummary();
        summary.setTotalLeavesTaken(125.5);
        summary.setTotalEmployees(45);
        summary.setAvgLeavePerEmployee(2.79);
        summary.setApprovalRate(87.5);
        summary.setPendingRequests(8);
        summary.setInsights(List.of(
                "Leave usage increased by 12% compared to last month",
                "Engineering department has highest leave utilization",
                "Sick leave claims have decreased in Q3"
        ));

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/leave-trend")
    @RequireRole({"HR_ADMIN", "MANAGER"})
    public ResponseEntity<List<LeaveTrendPoint>> getLeaveTrend(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer departmentId,
            Authentication authentication) {

        // Simplified implementation - would query actual trend data
        List<LeaveTrendPoint> trendPoints = List.of(
                new LeaveTrendPoint("Jan", 15.5),
                new LeaveTrendPoint("Feb", 12.0),
                new LeaveTrendPoint("Mar", 18.5),
                new LeaveTrendPoint("Apr", 10.0),
                new LeaveTrendPoint("May", 14.5),
                new LeaveTrendPoint("Jun", 8.0),
                new LeaveTrendPoint("Jul", 5.0),
                new LeaveTrendPoint("Aug", 6.5),
                new LeaveTrendPoint("Sep", 11.0),
                new LeaveTrendPoint("Oct", 13.5),
                new LeaveTrendPoint("Nov", 9.5),
                new LeaveTrendPoint("Dec", 7.0)
        );

        return ResponseEntity.ok(trendPoints);
    }

    @GetMapping("/department-summary")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<List<DepartmentSummary>> getDepartmentSummary(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication) {

        // Simplified implementation - would query actual department data
        List<DepartmentSummary> departmentSummaries = List.of(
                createDepartmentSummary(1, "Engineering", 15, 45.5, 3.03, 92.5),
                createDepartmentSummary(2, "Marketing", 8, 22.0, 2.75, 88.0),
                createDepartmentSummary(3, "Sales", 12, 38.5, 3.21, 85.5),
                createDepartmentSummary(4, "HR", 5, 12.5, 2.50, 95.0),
                createDepartmentSummary(5, "Finance", 5, 7.0, 1.40, 90.0)
        );

        return ResponseEntity.ok(departmentSummaries);
    }

    @GetMapping("/top-employees")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<List<TopEmployee>> getTopEmployees(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {

        // Simplified implementation - would query actual employee data
        List<TopEmployee> topEmployees = List.of(
                createTopEmployee(1, "John Smith", "Engineering", 12.5),
                createTopEmployee(2, "Sarah Johnson", "Marketing", 10.0),
                createTopEmployee(3, "Mike Wilson", "Sales", 9.5),
                createTopEmployee(4, "Emily Brown", "Engineering", 8.5),
                createTopEmployee(5, "David Lee", "Sales", 7.5)
        );

        return ResponseEntity.ok(topEmployees);
    }

    @PostMapping("/export")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<ReportExportJob> createExportJob(
            @RequestBody ReportExportRequest request,
            Authentication authentication) {

        // Create export job and return job ID
        ReportExportJob job = new ReportExportJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setStatus("PENDING");
        job.setDownloadUrl(null);

        return ResponseEntity.status(202).body(job);
    }

    @GetMapping("/export/{jobId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<ReportExportJob> getExportJobStatus(
            @PathVariable String jobId,
            Authentication authentication) {

        // Simplified implementation - would query actual job status
        ReportExportJob job = new ReportExportJob();
        job.setJobId(jobId);
        job.setStatus("READY");
        job.setDownloadUrl("/api/v1/reports/exports/" + jobId + ".xlsx");

        return ResponseEntity.ok(job);
    }

    // Helper methods
    private DepartmentSummary createDepartmentSummary(Integer id, String name, int totalEmployees, 
                                                       double totalLeaveDays, double avgPerEmployee, double approvalRate) {
        DepartmentSummary summary = new DepartmentSummary();
        summary.setDepartmentId(id);
        summary.setDepartmentName(name);
        summary.setTotalEmployees(totalEmployees);
        summary.setTotalDays(totalLeaveDays);
        summary.setAvgLeaveDaysPerEmployee(avgPerEmployee);
        summary.setApprovalRate(approvalRate);
        return summary;
    }

    private TopEmployee createTopEmployee(Integer userId, String fullName, String departmentName, double totalDaysTaken) {
        TopEmployee employee = new TopEmployee();
        employee.setUserId(userId);
        employee.setFullName(fullName);
        employee.setDepartmentName(departmentName);
        employee.setTotalDaysTaken(totalDaysTaken);
        return employee;
    }
}