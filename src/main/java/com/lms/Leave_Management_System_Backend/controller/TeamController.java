package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;

    public TeamController(UserRepository userRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          LeaveLedgerRepository leaveLedgerRepository) {
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
    }

    /**
     * Get paginated list of team members (Filtered by Role, Department, and Search Query at DB Level)
     */
    @GetMapping("/members")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<TeamMember>> listTeamMembers(
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        boolean isManager = "MANAGER".equalsIgnoreCase(currentUser.getRole().getRoleCode());
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("fullName").ascending());

        Page<User> usersPage = userRepository.findTeamMembers(
                isManager,
                currentUser.getId(),
                departmentId,
                (q != null && !q.trim().isEmpty()) ? q.trim() : null,
                pageable
        );

        List<TeamMember> memberDtos = usersPage.getContent().stream()
                .map(this::toTeamMember)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                usersPage.getNumber() + 1,
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, memberDtos, pageResponse));
    }

    /**
     * Get team calendar days with real database leave entries for a given month/year
     */
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

        // 1. Identify eligible user IDs based on authority
        List<User> eligibleUsers;
        if ("MANAGER".equalsIgnoreCase(currentUser.getRole().getRoleCode())) {
            eligibleUsers = userRepository.findByReportsToId(currentUser.getId());
        } else {
            eligibleUsers = userRepository.findAll();
        }

        if (departmentId != null) {
            eligibleUsers = eligibleUsers.stream()
                    .filter(u -> u.getDepartment() != null && u.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        if (eligibleUsers.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Long> userIds = eligibleUsers.stream().map(User::getId).collect(Collectors.toList());

        // 2. Fetch approved leaves within the target month boundaries
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        List<LeaveRequest> leaveRequests = leaveRequestRepository.findApprovedLeavesForUsers(userIds, monthStart, monthEnd);

        if (categoryId != null) {
            leaveRequests = leaveRequests.stream()
                    .filter(lr -> lr.getCategory() != null && lr.getCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
        }

        // 3. Map entries by target date
        Map<LocalDate, List<TeamCalendarEntry>> dailyEntriesMap = new HashMap<>();
        for (LeaveRequest leave : leaveRequests) {
            LocalDate start = leave.getStartDate().isBefore(monthStart) ? monthStart : leave.getStartDate();
            LocalDate end = leave.getEndDate().isAfter(monthEnd) ? monthEnd : leave.getEndDate();

            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                dailyEntriesMap.computeIfAbsent(date, k -> new ArrayList<>())
                        .add(toCalendarEntry(leave));
            }
        }

        // 4. Construct month view response
        List<TeamCalendarDay> calendarDays = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate currentDay = LocalDate.of(year, month, day);
            boolean isWeekend = currentDay.getDayOfWeek() == DayOfWeek.SATURDAY || currentDay.getDayOfWeek() == DayOfWeek.SUNDAY;

            if (!showWeekends && isWeekend) {
                continue;
            }

            List<TeamCalendarEntry> entries = dailyEntriesMap.getOrDefault(currentDay, Collections.emptyList());
            calendarDays.add(new TeamCalendarDay(currentDay, entries));
        }

        return ResponseEntity.ok(calendarDays);
    }

    /**
     * Get aggregate team leave summary grouped by category from the database
     */
    @GetMapping("/leave-summary")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<TeamLeaveSummary>> getTeamLeaveSummary(
            @RequestParam(required = false) Integer year,
            Authentication authentication) {

        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        int targetYear = (year != null) ? year : LocalDate.now().getYear();

        List<User> eligibleUsers;
        if ("MANAGER".equalsIgnoreCase(currentUser.getRole().getRoleCode())) {
            eligibleUsers = userRepository.findByReportsToId(currentUser.getId());
        } else {
            eligibleUsers = userRepository.findAll();
        }

        if (eligibleUsers.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Long> userIds = eligibleUsers.stream().map(User::getId).collect(Collectors.toList());

        List<TeamLeaveSummary> summary = leaveLedgerRepository.findTeamLeaveSummary(userIds, targetYear);

        return ResponseEntity.ok(summary);
    }

    // Helper Mapping Methods
    private TeamMember toTeamMember(User user) {
        TeamMember member = new TeamMember();
        member.setId(user.getId());
        member.setEmployeeCode(user.getEmployeeCode());
        member.setFullName(user.getName());
        if (user.getDepartment() != null) {
            member.setDepartmentId(user.getDepartment().getId());
            String deptName = user.getDepartment().getName();
            member.setDepartmentName(deptName != null ? deptName : "Unknown");
        }
        member.setDesignation(user.getDesignation());
        member.setEmail(user.getEmail());
        member.setPhone(user.getPhone());
        if (user.getEmploymentStatus() != null) {
            member.setStatus(user.getEmploymentStatus().name());
        }
        member.setAvatarUrl(user.getAvatarUrl());
        return member;
    }

    private TeamCalendarEntry toCalendarEntry(LeaveRequest leave) {
        User user = leave.getUser();
        TeamCalendarEntry entry = new TeamCalendarEntry();
        entry.setUserId(user.getId());
        entry.setFullName(user.getName());
        entry.setAvatarUrl(user.getAvatarUrl());
        if (leave.getCategory() != null) {
            entry.setCategoryId(leave.getCategory().getId());
            entry.setCategoryName(leave.getCategory().getCategoryName());
        }
        entry.setSessionType(leave.getSessionType().toString());
        return entry;
    }
}