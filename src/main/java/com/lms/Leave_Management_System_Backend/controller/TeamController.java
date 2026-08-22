package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final UserRepository userRepository;

    public TeamController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/members")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<TeamMember>> listTeamMembers(
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        Pageable pageable = PageRequest.of(page, limit, Sort.by("name").ascending());

        List<User> teamMembers;
        if (currentUser.getRole().getRoleCode().equals("MANAGER")) {
            // Managers see their direct reports
            teamMembers = userRepository.findByReportsToId(currentUser.getId());
        } else {
            // HR can see all employees
            teamMembers = userRepository.findAll();
        }

        // Apply filters
        if (departmentId != null) {
            teamMembers = teamMembers.stream()
                    .filter(u -> u.getDepartment() != null && u.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        if (q != null && !q.isEmpty()) {
            String searchLower = q.toLowerCase();
            teamMembers = teamMembers.stream()
                    .filter(u -> u.getName().toLowerCase().contains(searchLower) ||
                               u.getEmail().toLowerCase().contains(searchLower) ||
                               (u.getEmployeeCode() != null && u.getEmployeeCode().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
        }

        // Apply pagination manually
        int start = page * limit;
        int end = Math.min(start + limit, teamMembers.size());
        List<User> paginatedMembers = teamMembers.subList(start, end);

        List<TeamMember> memberDtos = paginatedMembers.stream()
                .map(this::toTeamMember)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(page, limit, teamMembers.size(), 
                (int) Math.ceil((double) teamMembers.size() / limit));

        return ResponseEntity.ok(new PaginatedResponse<>(true, memberDtos, pageResponse));
    }

    @GetMapping("/calendar")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<TeamCalendarDay>> getTeamCalendar(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "false") boolean showWeekends,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Simplified implementation - would query actual team calendar data
        List<TeamCalendarDay> calendarDays = List.of(
                createTeamCalendarDay(LocalDate.of(year, month, 1), List.of()),
                createTeamCalendarDay(LocalDate.of(year, month, 15), List.of(
                        createTeamCalendarEntry(1L, "John Doe", "/avatar1.jpg", 1, "Casual Leave", "FULL_DAY")
                ))
        );

        return ResponseEntity.ok(calendarDays);
    }

    @GetMapping("/leave-summary")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<TeamLeaveSummary>> getTeamLeaveSummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Simplified implementation - would query actual team leave summary
        List<TeamLeaveSummary> summary = List.of(
                new TeamLeaveSummary(1, "Casual Leave", 12.0),
                new TeamLeaveSummary(2, "Sick Leave", 6.0),
                new TeamLeaveSummary(3, "Annual Leave", 18.0)
        );

        return ResponseEntity.ok(summary);
    }

    private TeamMember toTeamMember(User user) {
        TeamMember member = new TeamMember();
        member.setId(user.getId());
        member.setEmployeeCode(user.getEmployeeCode());
        member.setFullName(user.getName());
        if (user.getDepartment() != null) {
            member.setDepartmentId(user.getDepartment().getId());
            // Safe getName() call with null check
            String deptName = user.getDepartment().getName();
            member.setDepartmentName(deptName != null ? deptName : "Unknown");
        }
        member.setDesignation(user.getDesignation());
        member.setEmail(user.getEmail());
        member.setPhone(user.getPhone());
        member.setStatus(user.getEmploymentStatus().name());
        member.setAvatarUrl(user.getAvatarUrl());
        return member;
    }

    private TeamCalendarDay createTeamCalendarDay(LocalDate date, List<TeamCalendarEntry> entries) {
        TeamCalendarDay day = new TeamCalendarDay();
        day.setDate(date);
        day.setEntries(entries);
        return day;
    }

    private TeamCalendarEntry createTeamCalendarEntry(Long userId, String fullName, String avatarUrl, 
                                                     Integer categoryId, String categoryName, String sessionType) {
        TeamCalendarEntry entry = new TeamCalendarEntry();
        entry.setUserId(userId);
        entry.setFullName(fullName);
        entry.setAvatarUrl(avatarUrl);
        entry.setCategoryId(categoryId);
        entry.setCategoryName(categoryName);
        entry.setSessionType(sessionType);
        return entry;
    }
}
