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
    public ResponseEntity<DashboardSummary> getDashboardSummary(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        DashboardSummary summary = new DashboardSummary();
        summary.setAvailableLeave(18.5);
        summary.setUsedLeave(7.5);
        summary.setPendingRequests(leaveRequestRepository.findByUserIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L1).size() + 
                                       leaveRequestRepository.findByUserIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L2).size());
        summary.setCompOffBalance(1.0);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/leave-trend")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveTrendPoint>> getLeaveTrend(
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Simplified implementation - would query actual leave data
        List<LeaveTrendPoint> trendPoints = List.of(
                new LeaveTrendPoint("Jan", 2.0),
                new LeaveTrendPoint("Feb", 1.5),
                new LeaveTrendPoint("Mar", 3.0),
                new LeaveTrendPoint("Apr", 0.5),
                new LeaveTrendPoint("May", 2.0),
                new LeaveTrendPoint("Jun", 1.0),
                new LeaveTrendPoint("Jul", 0.0),
                new LeaveTrendPoint("Aug", 0.0),
                new LeaveTrendPoint("Sep", 0.0),
                new LeaveTrendPoint("Oct", 0.0),
                new LeaveTrendPoint("Nov", 0.0),
                new LeaveTrendPoint("Dec", 0.0)
        );

        return ResponseEntity.ok(trendPoints);
    }

    @GetMapping("/leave-distribution")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveDistributionSlice>> getLeaveDistribution(
            @RequestParam(required = false) Integer year,
            Authentication authentication) {
        
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Simplified implementation - would query actual leave data
        List<LeaveDistributionSlice> distribution = List.of(
                new LeaveDistributionSlice("Casual Leave", 6.0, "#2563eb"),
                new LeaveDistributionSlice("Sick Leave", 3.0, "#16a34a"),
                new LeaveDistributionSlice("Annual Leave", 8.5, "#dc2626"),
                new LeaveDistributionSlice("Comp-Off", 1.0, "#9333ea")
        );

        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/hr-summary")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Object>> getHrSummary(Authentication authentication) {
        Map<String, Object> summary = new HashMap<>();
        List<User> allUsers = userRepository.findAll();
        summary.put("totalEmployees", allUsers.size());
        summary.put("activeEmployees", userRepository.findByEmploymentStatus(User.EmploymentStatus.ACTIVE).size());
        
        LocalDate today = LocalDate.now();
        List<LeaveRequest> allLeaveRequests = leaveRequestRepository.findAll();
        List<LeaveRequest> onLeaveToday = allLeaveRequests.stream()
                .filter(lr -> lr.getStatus() == LeaveRequest.RequestStatus.APPROVED && 
                             !lr.getStartDate().isAfter(today) && !lr.getEndDate().isBefore(today))
                .collect(java.util.stream.Collectors.toList());
        summary.put("onLeaveToday", onLeaveToday.size());
        
        summary.put("inactiveEmployees", userRepository.findByEmploymentStatus(User.EmploymentStatus.SEPARATED).size());

        return ResponseEntity.ok(summary);
    }
}
