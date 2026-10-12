package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveRequestRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    // Categories of this type are one-off/eligibility-based allowances (Maternity,
    // Paternity, Bereavement, etc.) rather than the general annual leave pool, so
    // they're excluded from the headline dashboard totals - same as they aren't
    // rolled into any single number on the Leave Ledger page either.
    private static final String SPECIAL_CATEGORY_TYPE = "SPECIAL";
    private static final String COMPENSATORY_CATEGORY_TYPE = "COMPENSATORY";

    private static final String[] DISTRIBUTION_COLORS = {
            "#2563eb", "#16a34a", "#dc2626", "#9333ea", "#f59e0b", "#0891b2"
    };

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveLedgerProvisioningService leaveLedgerProvisioningService;

    public DashboardController(
            UserRepository userRepository,
            LeaveRequestRepository leaveRequestRepository,
            LeaveLedgerProvisioningService leaveLedgerProvisioningService) {
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveLedgerProvisioningService = leaveLedgerProvisioningService;
    }

    @GetMapping("/summary")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<DashboardSummary> getDashboardSummary(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        int year = LocalDate.now().getYear();

        List<LeaveLedger> ledgerEntries = leaveLedgerProvisioningService.getOrInitializeLedger(user, year);

        // Same ledger rows the Leave Ledger page reads, so the two screens can
        // never drift apart the way the old hardcoded stub values did.
        BigDecimal availableLeave = BigDecimal.ZERO;
        BigDecimal usedLeave = BigDecimal.ZERO;
        BigDecimal compOffBalance = BigDecimal.ZERO;

        for (LeaveLedger entry : ledgerEntries) {
            String categoryType = entry.getCategory().getCategoryType();

            if (COMPENSATORY_CATEGORY_TYPE.equalsIgnoreCase(categoryType)) {
                compOffBalance = compOffBalance.add(entry.getClosingBalance());
            }

            if (SPECIAL_CATEGORY_TYPE.equalsIgnoreCase(categoryType)) {
                continue;
            }

            availableLeave = availableLeave.add(entry.getClosingBalance());
            usedLeave = usedLeave.add(entry.getUsed());
        }

        DashboardSummary summary = new DashboardSummary();
        summary.setAvailableLeave(roundToOneDecimal(availableLeave));
        summary.setUsedLeave(roundToOneDecimal(usedLeave));
        summary.setPendingRequests(
                leaveRequestRepository.findByUserIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L1).size() +
                        leaveRequestRepository.findByUserIdAndStatus(user.getId(), LeaveRequest.RequestStatus.PENDING_L2).size());
        summary.setCompOffBalance(roundToOneDecimal(compOffBalance));

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/leave-trend")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveTrendSeries>> getLeaveTrend(
            @RequestParam(required = false) Integer year,
            Authentication authentication) {

        User user = resolveCurrentUser(authentication);
        int targetYear = year != null ? year : LocalDate.now().getYear();

        List<LeaveLedger> ledgerEntries = leaveLedgerProvisioningService.getOrInitializeLedger(user, targetYear);

        List<LeaveRequest> approvedThisYear = leaveRequestRepository.findByUserId(user.getId()).stream()
                .filter(request -> request.getStatus() == LeaveRequest.RequestStatus.APPROVED)
                .filter(request -> request.getStartDate() != null && request.getStartDate().getYear() == targetYear)
                .toList();

        // One line per leave category (skipping SPECIAL ones like Maternity/
        // Paternity, same as the summary and distribution endpoints) so the
        // chart shows how usage is actually split across leave types, not
        // just a single combined total.
        List<LeaveTrendSeries> series = new ArrayList<>();
        int colorIndex = 0;
        for (LeaveLedger entry : ledgerEntries) {
            var category = entry.getCategory();
            if (SPECIAL_CATEGORY_TYPE.equalsIgnoreCase(category.getCategoryType())) {
                continue;
            }

            double[] daysByMonth = new double[12];
            for (LeaveRequest request : approvedThisYear) {
                if (request.getCategory() == null || !request.getCategory().getId().equals(category.getId())) {
                    continue;
                }
                int monthIndex = request.getStartDate().getMonthValue() - 1;
                double days = request.getTotalDays() != null ? request.getTotalDays().doubleValue() : 0.0;
                daysByMonth[monthIndex] += days;
            }

            List<LeaveTrendPoint> points = new ArrayList<>();
            for (int month = 1; month <= 12; month++) {
                String label = LocalDate.of(targetYear, month, 1)
                        .getMonth()
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                points.add(new LeaveTrendPoint(label, roundToOneDecimal(BigDecimal.valueOf(daysByMonth[month - 1]))));
            }

            series.add(new LeaveTrendSeries(
                    category.getCategoryName(),
                    DISTRIBUTION_COLORS[colorIndex % DISTRIBUTION_COLORS.length],
                    points
            ));
            colorIndex++;
        }

        return ResponseEntity.ok(series);
    }

    @GetMapping("/leave-distribution")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveDistributionSlice>> getLeaveDistribution(
            @RequestParam(required = false) Integer year,
            Authentication authentication) {

        User user = resolveCurrentUser(authentication);
        int targetYear = year != null ? year : LocalDate.now().getYear();

        List<LeaveLedger> ledgerEntries = leaveLedgerProvisioningService.getOrInitializeLedger(user, targetYear);

        // Every applicable category is listed, even ones with zero usage so
        // far this year, so the breakdown shows the employee's full leave
        // picture rather than only the categories they've already used.
        List<LeaveDistributionSlice> distribution = new ArrayList<>();
        int colorIndex = 0;
        for (LeaveLedger entry : ledgerEntries) {
            String categoryType = entry.getCategory().getCategoryType();
            if (SPECIAL_CATEGORY_TYPE.equalsIgnoreCase(categoryType)) {
                continue;
            }

            distribution.add(new LeaveDistributionSlice(
                    entry.getCategory().getCategoryName(),
                    roundToOneDecimal(entry.getUsed()),
                    DISTRIBUTION_COLORS[colorIndex % DISTRIBUTION_COLORS.length]
            ));
            colorIndex++;
        }

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

    private User resolveCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private double roundToOneDecimal(BigDecimal value) {
        if (value == null) {
            return 0.0;
        }
        return value.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
