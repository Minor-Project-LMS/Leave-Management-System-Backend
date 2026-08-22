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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/dashboard")
public class ManagerDashboardController {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public ManagerDashboardController(UserRepository userRepository, LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping("/summary")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ManagerDashboardSummary> getManagerDashboardSummary(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        ManagerDashboardSummary summary = new ManagerDashboardSummary();
        List<User> teamMembers = userRepository.findByReportsToId(user.getId());
        summary.setTotalTeamSize(teamMembers.size());
        summary.setActiveEmployees((int) teamMembers.stream()
                .filter(m -> m.getEmploymentStatus() == User.EmploymentStatus.ACTIVE)
                .count());
        
        List<LeaveRequest> pendingL1 = leaveRequestRepository.findByCurrentApproverIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L1);
        List<LeaveRequest> pendingL2 = leaveRequestRepository.findByCurrentApproverIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L2);
        summary.setPendingApprovals(pendingL1.size() + pendingL2.size());
        summary.setPendingUrgent((pendingL1.size() + pendingL2.size()) > 3); // Example urgency logic
        
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = thisMonth.plusMonths(1).minusDays(1);
        int leavesThisMonth = 0;
        for (User teamMember : teamMembers) {
            leavesThisMonth += leaveRequestRepository.findByUserIdAndStatusAndStartDateBetween(
                    teamMember.getId(), LeaveRequest.RequestStatus.APPROVED, thisMonth, endOfMonth).size();
        }
        summary.setLeavesThisMonth(leavesThisMonth);
        summary.setLeavesThisMonthChangePct(12.0); // Example percentage change
        summary.setAvailableBalanceAvg(16.2); // Example average balance

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/leave-trend")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveTrendPoint>> getManagerLeaveTrend(
            @RequestParam(defaultValue = "6") int months,
            Authentication authentication) {
        
        // Simplified implementation - would query actual team leave trend data
        List<LeaveTrendPoint> trendPoints = List.of(
                new LeaveTrendPoint("Jan", 15.5),
                new LeaveTrendPoint("Feb", 12.0),
                new LeaveTrendPoint("Mar", 18.5),
                new LeaveTrendPoint("Apr", 10.0),
                new LeaveTrendPoint("May", 14.5),
                new LeaveTrendPoint("Jun", 8.0)
        );

        return ResponseEntity.ok(trendPoints);
    }

    @GetMapping("/leave-distribution")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveDistributionSlice>> getManagerLeaveDistribution(Authentication authentication) {
        
        // Simplified implementation - would query actual team leave distribution data
        List<LeaveDistributionSlice> distribution = List.of(
                new LeaveDistributionSlice("Casual Leave", 12.0, "#2563eb"),
                new LeaveDistributionSlice("Sick Leave", 6.0, "#16a34a"),
                new LeaveDistributionSlice("Annual Leave", 15.0, "#dc2626"),
                new LeaveDistributionSlice("Comp-Off", 2.0, "#9333ea")
        );

        return ResponseEntity.ok(distribution);
    }
}