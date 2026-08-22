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

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/team")
public class ManagerTeamController {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public ManagerTeamController(UserRepository userRepository, LeaveRequestRepository leaveRequestRepository) {
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping("/leave-overview")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<TeamLeaveOverviewRow>> getTeamLeaveOverview(Authentication authentication) {
        
        // Simplified implementation - would query actual team leave overview by department
        List<TeamLeaveOverviewRow> overview = List.of(
                new TeamLeaveOverviewRow("Engineering", 14, 2, 8, 15.6),
                new TeamLeaveOverviewRow("Marketing", 8, 1, 4, 12.3),
                new TeamLeaveOverviewRow("Sales", 10, 3, 6, 14.8)
        );

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/upcoming-leaves")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<UpcomingTeamLeave>> getUpcomingTeamLeaves(
            @RequestParam(defaultValue = "5") int limit,
            Authentication authentication) {
        
        // Simplified implementation - would query actual upcoming team leaves
        List<UpcomingTeamLeave> upcomingLeaves = List.of(
                new UpcomingTeamLeave("27", "MAY", "Sneha Patel", "Earned Leave", "27 May - 29 May 2024"),
                new UpcomingTeamLeave("30", "MAY", "Rahul Kumar", "Casual Leave", "30 May 2024"),
                new UpcomingTeamLeave("02", "JUN", "Priya Sharma", "Sick Leave", "02 Jun - 03 Jun 2024")
        );

        return ResponseEntity.ok(upcomingLeaves);
    }
}