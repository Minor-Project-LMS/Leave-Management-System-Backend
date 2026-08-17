package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public DashboardController(UserRepository userRepository, LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping("/summary")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<DashboardSummary>> getDashboardSummary(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        DashboardSummary summary = new DashboardSummary();
        summary.setUserName(user.getName());
        summary.setRole(user.getRole().getRoleCode());
        if (user.getDepartment() != null) {
            summary.setDepartmentName(user.getDepartment().getName());
        }

        // Leave balances (simplified - in real implementation, would query leave_ledger)
        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put("ANNUAL", new BigDecimal("12.0"));
        balances.put("SICK", new BigDecimal("6.0"));
        balances.put("CASUAL", new BigDecimal("3.0"));
        summary.setLeaveBalances(balances);

        // Pending approvals (for managers)
        if ("MANAGER".equals(user.getRole().getRoleCode()) || "HR_ADMIN".equals(user.getRole().getRoleCode())) {
            List<LeaveRequest> pendingL1 = leaveRequestRepository.findByCurrentApproverIdAndStatus(
                    user.getId(), LeaveRequest.RequestStatus.PENDING_L1);
            List<LeaveRequest> pendingL2 = leaveRequestRepository.findByCurrentApproverIdAndStatus(
                    user.getId(), LeaveRequest.RequestStatus.PENDING_L2);
            summary.setPendingApprovalsCount(pendingL1.size() + pendingL2.size());
        } else {
            summary.setPendingApprovalsCount(0);
        }

        // Upcoming leaves (approved leaves starting in next 30 days)
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);
        List<LeaveRequest> upcomingLeaves = leaveRequestRepository.findByUserIdAndStatusAndStartDateBetween(
                user.getId(), LeaveRequest.RequestStatus.APPROVED, today, thirtyDaysLater);
        summary.setUpcomingLeavesCount(upcomingLeaves.size());

        // Team on leave today (for managers)
        if ("MANAGER".equals(user.getRole().getRoleCode()) || "HR_ADMIN".equals(user.getRole().getRoleCode())) {
            List<User> teamMembers = userRepository.findByReportsToId(user.getId());
            int teamOnLeave = 0;
            for (User teamMember : teamMembers) {
                List<LeaveRequest> todayLeaves = leaveRequestRepository.findByUserIdAndStatusAndStartDateBetween(
                        teamMember.getId(), LeaveRequest.RequestStatus.APPROVED, today, today);
                if (!todayLeaves.isEmpty()) {
                    teamOnLeave++;
                }
            }
            summary.setTeamOnLeaveToday(teamOnLeave);
        } else {
            summary.setTeamOnLeaveToday(0);
        }

        return ResponseEntity.ok(new ApiResponse<>(true, summary));
    }

    @GetMapping("/my-leave-balance")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getMyLeaveBalance(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Simplified implementation - in real system, would query leave_ledger table
        Map<String, BigDecimal> balances = new HashMap<>();
        balances.put("ANNUAL", new BigDecimal("12.0"));
        balances.put("SICK", new BigDecimal("6.0"));
        balances.put("CASUAL", new BigDecimal("3.0"));
        balances.put("COMP_OFF", new BigDecimal("0.0"));

        return ResponseEntity.ok(new ApiResponse<>(true, balances));
    }

    @GetMapping("/upcoming-holidays")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getUpcomingHolidays() {
        // Simplified implementation - in real system, would query holidays table
        List<Map<String, String>> holidays = List.of(
                Map.of("date", "2024-12-25", "name", "Christmas Day"),
                Map.of("date", "2025-01-01", "name", "New Year's Day"),
                Map.of("date", "2025-01-26", "name", "Republic Day")
        );

        return ResponseEntity.ok(new ApiResponse<>(true, holidays));
    }
}
