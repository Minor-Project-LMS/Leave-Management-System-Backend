package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.DepartmentSummary;
import com.lms.Leave_Management_System_Backend.dto.LeaveTrendPoint;
import com.lms.Leave_Management_System_Backend.dto.LeaveTrendSeries;
import com.lms.Leave_Management_System_Backend.dto.ReportDistributionItem;
import com.lms.Leave_Management_System_Backend.dto.ReportDistributionResponse;
import com.lms.Leave_Management_System_Backend.dto.ReportSummary;
import com.lms.Leave_Management_System_Backend.dto.TopEmployee;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import com.lms.Leave_Management_System_Backend.model.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// All the numbers on the HR Reports & Analytics page (and the department
// panels on the HR Dashboard / Employee Management pages) come from here.
// These used to be hardcoded sample values in ReportsController.
//
// Deliberately free of Spring/JPA wiring: it works on lists the controller
// has already loaded, so the definitions below can be tested directly.
//
// Definitions (kept in one place so they're easy to defend):
//   * "Leave taken" = APPROVED requests, counted by the month/day the leave
//     STARTS. A request spanning two months is attributed to its start
//     month — the same rule the Employee Dashboard's trend chart uses.
//   * "Active employees" = users whose employment status is ACTIVE, all
//     roles (HR staff take leave too).
//   * "Approval rate" = approved / (approved + rejected) for requests that
//     start in the period. Pending, withdrawn, cancelled and draft requests
//     aren't "decided", so they're left out. null when nothing was decided.
//   * "Pending requests" = requests awaiting a decision right now,
//     regardless of period — it's a to-do count, not a period statistic.
//   * "Utilization" = leave days used / days entitled (opening balance +
//     accrued + carried forward) from this year's ledger rows.
public class ReportsAggregator {

    private static final String[] PALETTE = {"#2563eb", "#16a34a", "#dc2626", "#9333ea", "#f59e0b", "#0891b2"};
    private static final String[] MONTH_ABBR = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private final List<LeaveRequest> requests;
    private final List<User> users;
    private final List<Department> departments;
    private final List<LeaveCategory> categories;
    private final List<LeaveLedger> ledgers;
    private final LocalDate today;

    public ReportsAggregator(List<LeaveRequest> requests, List<User> users, List<Department> departments,
                             List<LeaveCategory> categories, List<LeaveLedger> ledgers, LocalDate today) {
        this.requests = requests;
        this.users = users;
        this.departments = departments;
        this.categories = categories;
        this.ledgers = ledgers;
        this.today = today;
    }

    // ---------------------------------------------------------------- period

    // Defaults to the current calendar year — which is the range the page
    // sends — and rejects an inverted range rather than silently returning
    // zeros.
    public LocalDate[] resolvePeriod(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = from;
        LocalDate resolvedTo = to;
        if (resolvedFrom == null && resolvedTo == null) {
            resolvedFrom = LocalDate.of(today.getYear(), 1, 1);
            resolvedTo = LocalDate.of(today.getYear(), 12, 31);
        } else if (resolvedFrom == null) {
            resolvedFrom = LocalDate.of(resolvedTo.getYear(), 1, 1);
        } else if (resolvedTo == null) {
            resolvedTo = LocalDate.of(resolvedFrom.getYear(), 12, 31);
        }
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("dateFrom must not be after dateTo.");
        }
        return new LocalDate[]{resolvedFrom, resolvedTo};
    }

    // --------------------------------------------------------------- summary

    public ReportSummary summary(LocalDate from, LocalDate to, Integer departmentId) {
        LocalDate[] period = resolvePeriod(from, to);

        List<LeaveRequest> approved = approvedInPeriod(period, departmentId);
        BigDecimal totalDays = sumDays(approved);

        long employees = users.stream()
                .filter(this::isActive)
                .filter(u -> inDepartment(u, departmentId))
                .count();

        long rejected = requests.stream()
                .filter(r -> r.getStatus() == LeaveRequest.RequestStatus.REJECTED)
                .filter(r -> startsInPeriod(r, period))
                .filter(r -> requesterInDepartment(r, departmentId))
                .count();
        long decided = approved.size() + rejected;

        long pending = requests.stream()
                .filter(this::isPending)
                .filter(r -> requesterInDepartment(r, departmentId))
                .count();

        ReportSummary summary = new ReportSummary();
        summary.setTotalLeavesTaken(round(totalDays, 1));
        summary.setTotalEmployees((int) employees);
        summary.setAvgLeavePerEmployee(employees > 0
                ? round(totalDays.divide(BigDecimal.valueOf(employees), 4, RoundingMode.HALF_UP), 2)
                : 0.0);
        summary.setApprovalRate(decided > 0
                ? round(BigDecimal.valueOf(approved.size() * 100.0 / decided), 1)
                : null);
        summary.setPendingRequests((int) pending);
        summary.setUtilizationPct(utilization(departmentId, period[1].getYear()));
        summary.setInsights(insights(period, departmentId, approved, totalDays, pending));
        return summary;
    }

    // -------------------------------------------------------------- insights

    // Only statements that can be read straight off the data — no invented
    // "usage is up 12%" style trends.
    private List<String> insights(LocalDate[] period, Integer departmentId, List<LeaveRequest> approved,
                                  BigDecimal totalDays, long pending) {
        List<String> insights = new ArrayList<>();

        if (totalDays.signum() > 0) {
            Map<String, BigDecimal> byType = new HashMap<>();
            for (LeaveRequest r : approved) {
                if (r.getCategory() == null) continue;
                byType.merge(r.getCategory().getName(), days(r), BigDecimal::add);
            }
            byType.entrySet().stream()
                    .max(Map.Entry.<String, BigDecimal>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .ifPresent(top -> insights.add(top.getKey() + " is the most used leave type: "
                            + fmt(top.getValue()) + " day(s), " + percent(top.getValue(), totalDays)
                            + "% of all leave taken."));

            Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
            for (LeaveRequest r : approved) {
                byMonth.merge(YearMonth.from(r.getStartDate()), days(r), BigDecimal::add);
            }
            byMonth.entrySet().stream()
                    .max(Map.Entry.<YearMonth, BigDecimal>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .ifPresent(top -> insights.add(top.getKey().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                            + " " + top.getKey().getYear() + " has the most leave: "
                            + fmt(top.getValue()) + " day(s)."));

            if (departmentId == null && departments.size() > 1) {
                Map<String, BigDecimal> byDept = new HashMap<>();
                for (LeaveRequest r : approved) {
                    Department d = r.getUser() != null ? r.getUser().getDepartment() : null;
                    if (d == null) continue;
                    byDept.merge(d.getDepartmentName(), days(r), BigDecimal::add);
                }
                byDept.entrySet().stream()
                        .max(Map.Entry.<String, BigDecimal>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                        .ifPresent(top -> insights.add(top.getKey() + " has the highest leave usage: "
                                + fmt(top.getValue()) + " day(s)."));
            }
        }

        if (pending > 0) {
            insights.add(pending + " leave request(s) are awaiting a decision.");
        }

        long escalated = requests.stream()
                .filter(LeaveRequest::isEscalated)
                .filter(r -> r.getEscalatedAt() != null && withinPeriod(r.getEscalatedAt().toLocalDate(), period))
                .filter(r -> requesterInDepartment(r, departmentId))
                .count();
        if (escalated > 0) {
            insights.add(escalated + " request(s) sat unanswered past the approval SLA and were escalated to HR.");
        }

        BigDecimal lop = approved.stream()
                .map(r -> r.getLopDays() != null ? r.getLopDays() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lop.signum() > 0) {
            insights.add(fmt(lop) + " day(s) of Loss of Pay were recorded on approved leave.");
        }

        return insights;
    }

    // ----------------------------------------------------------------- trend

    public List<LeaveTrendPoint> monthlyTrend(int year, Integer departmentId) {
        BigDecimal[] byMonth = zeroMonths();
        for (LeaveRequest r : requests) {
            if (!isApprovedStartingIn(r, year) || !requesterInDepartment(r, departmentId)) continue;
            int index = r.getStartDate().getMonthValue() - 1;
            byMonth[index] = byMonth[index].add(days(r));
        }
        return toPoints(byMonth);
    }

    // One line per leave type that actually has leave in that year, so the
    // legend isn't padded with unused or custom types sitting at zero.
    public List<LeaveTrendSeries> trendByType(int year, Integer departmentId) {
        Map<Integer, BigDecimal[]> byCategory = new HashMap<>();
        for (LeaveRequest r : requests) {
            if (!isApprovedStartingIn(r, year) || !requesterInDepartment(r, departmentId)) continue;
            if (r.getCategory() == null || r.getCategory().getId() == null) continue;
            BigDecimal[] months = byCategory.computeIfAbsent(r.getCategory().getId(), k -> zeroMonths());
            int index = r.getStartDate().getMonthValue() - 1;
            months[index] = months[index].add(days(r));
        }

        List<LeaveTrendSeries> series = new ArrayList<>();
        List<LeaveCategory> ordered = orderedCategories();
        for (int i = 0; i < ordered.size(); i++) {
            LeaveCategory category = ordered.get(i);
            BigDecimal[] months = byCategory.get(category.getId());
            if (months == null) continue;
            series.add(new LeaveTrendSeries(category.getName(), colorFor(i), toPoints(months)));
        }
        return series;
    }

    // ---------------------------------------------------------- distribution

    public ReportDistributionResponse distribution(LocalDate from, LocalDate to, Integer departmentId) {
        LocalDate[] period = resolvePeriod(from, to);

        Map<Integer, BigDecimal> byCategory = new HashMap<>();
        for (LeaveRequest r : approvedInPeriod(period, departmentId)) {
            if (r.getCategory() == null || r.getCategory().getId() == null) continue;
            byCategory.merge(r.getCategory().getId(), days(r), BigDecimal::add);
        }

        List<ReportDistributionItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        List<LeaveCategory> ordered = orderedCategories();
        for (int i = 0; i < ordered.size(); i++) {
            LeaveCategory category = ordered.get(i);
            BigDecimal value = byCategory.get(category.getId());
            if (value == null || value.signum() <= 0) continue;
            total = total.add(value);
            items.add(new ReportDistributionItem(category.getName(), round(value, 1), colorFor(i)));
        }
        items.sort(Comparator.comparing(ReportDistributionItem::getValue).reversed()
                .thenComparing(ReportDistributionItem::getLabel));
        return new ReportDistributionResponse(round(total, 1), items);
    }

    // ------------------------------------------------------------ departments

    public List<DepartmentSummary> departmentSummary(LocalDate from, LocalDate to) {
        LocalDate[] period = resolvePeriod(from, to);
        int ledgerYear = period[1].getYear();

        List<DepartmentSummary> rows = new ArrayList<>();
        List<Department> ordered = departments.stream()
                .sorted(Comparator.comparing(Department::getDepartmentName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        for (Department department : ordered) {
            Integer departmentId = department.getId();

            long employees = users.stream()
                    .filter(this::isActive)
                    .filter(u -> inDepartment(u, departmentId))
                    .count();

            List<LeaveRequest> approved = approvedInPeriod(period, departmentId);
            BigDecimal totalDays = sumDays(approved);

            long rejected = requests.stream()
                    .filter(r -> r.getStatus() == LeaveRequest.RequestStatus.REJECTED)
                    .filter(r -> startsInPeriod(r, period))
                    .filter(r -> requesterInDepartment(r, departmentId))
                    .count();
            long decided = approved.size() + rejected;

            DepartmentSummary row = new DepartmentSummary();
            row.setDepartmentId(departmentId);
            row.setDepartmentName(department.getDepartmentName());
            row.setTotalEmployees((int) employees);
            row.setTotalDays(round(totalDays, 1));
            row.setAvgLeaveDaysPerEmployee(employees > 0
                    ? round(totalDays.divide(BigDecimal.valueOf(employees), 4, RoundingMode.HALF_UP), 2)
                    : 0.0);
            row.setApprovalRate(decided > 0 ? round(BigDecimal.valueOf(approved.size() * 100.0 / decided), 1) : null);
            row.setUtilizationPct(utilization(departmentId, ledgerYear));
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------- top employees

    public List<TopEmployee> topEmployees(LocalDate from, LocalDate to, int limit, Integer departmentId) {
        LocalDate[] period = resolvePeriod(from, to);

        Map<Long, BigDecimal> daysByUser = new HashMap<>();
        Map<Long, User> userById = new HashMap<>();
        for (LeaveRequest r : approvedInPeriod(period, departmentId)) {
            User user = r.getUser();
            if (user == null || user.getId() == null) continue;
            daysByUser.merge(user.getId(), days(r), BigDecimal::add);
            userById.put(user.getId(), user);
        }

        return daysByUser.entrySet().stream()
                .sorted((a, b) -> {
                    int byDays = b.getValue().compareTo(a.getValue());
                    if (byDays != 0) return byDays;
                    return String.valueOf(userById.get(a.getKey()).getFullName())
                            .compareToIgnoreCase(String.valueOf(userById.get(b.getKey()).getFullName()));
                })
                .limit(Math.max(limit, 0))
                .map(entry -> {
                    User user = userById.get(entry.getKey());
                    TopEmployee top = new TopEmployee();
                    top.setUserId(user.getId().intValue());
                    top.setFullName(user.getFullName());
                    top.setDepartmentName(user.getDepartment() != null ? user.getDepartment().getDepartmentName() : "—");
                    top.setTotalDaysTaken(round(entry.getValue(), 1));
                    return top;
                })
                .collect(Collectors.toList());
    }

    // --------------------------------------------------------------- helpers

    // Used / entitled for one fiscal year, across whichever employees have
    // ledger rows (a ledger row is created the first time someone's balance
    // is needed, so people who've never touched leave aren't in the
    // denominator). 0 when there's no entitlement on record.
    private double utilization(Integer departmentId, int ledgerYear) {
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal entitled = BigDecimal.ZERO;
        for (LeaveLedger ledger : ledgers) {
            if (ledger.getFiscalYear() == null || ledger.getFiscalYear() != ledgerYear) continue;
            if (ledger.getUser() == null || !inDepartment(ledger.getUser(), departmentId)) continue;
            used = used.add(orZero(ledger.getUsed()));
            entitled = entitled.add(orZero(ledger.getOpeningBalance()))
                    .add(orZero(ledger.getAccrued()))
                    .add(orZero(ledger.getCarriedForward()));
        }
        if (entitled.signum() <= 0) return 0.0;
        return round(used.multiply(BigDecimal.valueOf(100)).divide(entitled, 4, RoundingMode.HALF_UP), 1);
    }

    private List<LeaveRequest> approvedInPeriod(LocalDate[] period, Integer departmentId) {
        return requests.stream()
                .filter(r -> r.getStatus() == LeaveRequest.RequestStatus.APPROVED)
                .filter(r -> startsInPeriod(r, period))
                .filter(r -> requesterInDepartment(r, departmentId))
                .collect(Collectors.toList());
    }

    private boolean isApprovedStartingIn(LeaveRequest r, int year) {
        return r.getStatus() == LeaveRequest.RequestStatus.APPROVED
                && r.getStartDate() != null
                && r.getStartDate().getYear() == year;
    }

    private boolean startsInPeriod(LeaveRequest r, LocalDate[] period) {
        return r.getStartDate() != null && withinPeriod(r.getStartDate(), period);
    }

    private boolean withinPeriod(LocalDate date, LocalDate[] period) {
        return !date.isBefore(period[0]) && !date.isAfter(period[1]);
    }

    private boolean isPending(LeaveRequest r) {
        return r.getStatus() == LeaveRequest.RequestStatus.PENDING_L1
                || r.getStatus() == LeaveRequest.RequestStatus.PENDING_L2;
    }

    private boolean isActive(User u) {
        return u.getEmploymentStatus() == User.EmploymentStatus.ACTIVE;
    }

    private boolean inDepartment(User u, Integer departmentId) {
        if (departmentId == null) return true;
        return u.getDepartment() != null && departmentId.equals(u.getDepartment().getId());
    }

    private boolean requesterInDepartment(LeaveRequest r, Integer departmentId) {
        if (departmentId == null) return true;
        return r.getUser() != null && inDepartment(r.getUser(), departmentId);
    }

    private BigDecimal days(LeaveRequest r) {
        return orZero(r.getTotalDays());
    }

    private BigDecimal sumDays(List<LeaveRequest> list) {
        return list.stream().map(this::days).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // Stable by category id, so a leave type keeps the same colour in every
    // chart and no matter which other types have data in the period.
    private List<LeaveCategory> orderedCategories() {
        return categories.stream()
                .sorted(Comparator.comparing(LeaveCategory::getId, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());
    }

    private String colorFor(int index) {
        return PALETTE[index % PALETTE.length];
    }

    private BigDecimal[] zeroMonths() {
        BigDecimal[] months = new BigDecimal[12];
        for (int i = 0; i < 12; i++) months[i] = BigDecimal.ZERO;
        return months;
    }

    private List<LeaveTrendPoint> toPoints(BigDecimal[] byMonth) {
        List<LeaveTrendPoint> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            points.add(new LeaveTrendPoint(MONTH_ABBR[i], round(byMonth[i], 1)));
        }
        return points;
    }

    private double round(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String fmt(BigDecimal value) {
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
    }

    private String percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) return "0";
        return String.valueOf(part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).intValue());
    }

}
