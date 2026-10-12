package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReportsAggregator is pure (no Spring/JPA), so the reporting definitions
 * are tested directly against hand-built data for fiscal year 2030.
 */
class ReportsAggregatorTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 6, 15);

    private Department engineering;
    private Department sales;
    private User alice;   // Engineering
    private User bob;     // Engineering
    private User carol;   // Sales
    private User dave;    // Sales, separated
    private LeaveCategory casual;
    private LeaveCategory sick;

    private List<LeaveRequest> requests;
    private List<LeaveLedger> ledgers;

    @BeforeEach
    void setUp() {
        engineering = department(1, "Engineering");
        sales = department(2, "Sales");
        alice = withDept(employee(1L, "alice@lms.com"), "Alice", engineering);
        bob = withDept(employee(2L, "bob@lms.com"), "Bob", engineering);
        carol = withDept(employee(3L, "carol@lms.com"), "Carol", sales);
        dave = withDept(employee(4L, "dave@lms.com"), "Dave", sales);
        dave.setEmploymentStatus(User.EmploymentStatus.SEPARATED);
        casual = category(1, "Casual Leave");
        sick = category(2, "Sick Leave");

        requests = new ArrayList<>(List.of(
                req(1L, alice, casual, "2030-01-10", "3", LeaveRequest.RequestStatus.APPROVED),
                req(2L, alice, sick, "2030-02-05", "1", LeaveRequest.RequestStatus.APPROVED),
                req(3L, bob, casual, "2030-01-20", "2", LeaveRequest.RequestStatus.APPROVED),
                req(4L, carol, casual, "2030-03-01", "4", LeaveRequest.RequestStatus.APPROVED),
                req(5L, carol, sick, "2030-03-10", "2", LeaveRequest.RequestStatus.REJECTED),
                req(6L, bob, sick, "2030-04-01", "1", LeaveRequest.RequestStatus.PENDING_L1),
                req(7L, alice, casual, "2029-12-20", "5", LeaveRequest.RequestStatus.APPROVED) // previous year
        ));

        LeaveLedger aliceCasual = ledger(alice, casual, 2030, "8");
        aliceCasual.setOpeningBalance(new BigDecimal("12"));
        aliceCasual.setUsed(new BigDecimal("3"));
        LeaveLedger carolCasual = ledger(carol, casual, 2030, "8");
        carolCasual.setOpeningBalance(new BigDecimal("8"));
        carolCasual.setUsed(new BigDecimal("4"));
        ledgers = List.of(aliceCasual, carolCasual);
    }

    private static User withDept(User user, String name, Department d) {
        user.setName(name);
        user.setDepartment(d);
        return user;
    }

    private static LeaveRequest req(Long id, User u, LeaveCategory c, String start, String days,
                                    LeaveRequest.RequestStatus status) {
        LocalDate s = LocalDate.parse(start);
        return leaveRequest(id, u, c, s, s, days, status);
    }

    private ReportsAggregator aggregator() {
        return new ReportsAggregator(requests, List.of(alice, bob, carol, dave),
                List.of(sales, engineering), List.of(sick, casual), ledgers, TODAY);
    }

    @Nested
    class ResolvePeriod {

        @Test
        void defaultsToCurrentCalendarYear() {
            LocalDate[] p = aggregator().resolvePeriod(null, null);
            assertThat(p).containsExactly(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31));
        }

        @Test
        void fillsMissingEndFromTheOtherSide() {
            assertThat(aggregator().resolvePeriod(LocalDate.of(2029, 3, 1), null))
                    .containsExactly(LocalDate.of(2029, 3, 1), LocalDate.of(2029, 12, 31));
            assertThat(aggregator().resolvePeriod(null, LocalDate.of(2029, 3, 1)))
                    .containsExactly(LocalDate.of(2029, 1, 1), LocalDate.of(2029, 3, 1));
        }

        @Test
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> aggregator().resolvePeriod(LocalDate.of(2030, 5, 1), LocalDate.of(2030, 1, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Summary {

        @Test
        @DisplayName("org-wide numbers for 2030")
        void orgWide() {
            ReportSummary s = aggregator().summary(null, null, null);

            assertThat(s.getTotalLeavesTaken()).isEqualTo(10.0);     // 3 + 1 + 2 + 4 (2029 excluded)
            assertThat(s.getTotalEmployees()).isEqualTo(3);          // Dave is separated
            assertThat(s.getAvgLeavePerEmployee()).isEqualTo(3.33);
            assertThat(s.getApprovalRate()).isEqualTo(80.0);         // 4 approved / 5 decided
            assertThat(s.getPendingRequests()).isEqualTo(1);
            assertThat(s.getUtilizationPct()).isEqualTo(35.0);       // used 7 / entitled 20
            assertThat(s.getInsights())
                    .anyMatch(i -> i.startsWith("Casual Leave is the most used leave type: 9 day(s), 90%"))
                    .anyMatch(i -> i.startsWith("January 2030 has the most leave: 5 day(s)"))
                    .anyMatch(i -> i.startsWith("Engineering has the highest leave usage: 6 day(s)"))
                    .anyMatch(i -> i.equals("1 leave request(s) are awaiting a decision."));
        }

        @Test
        void filteredByDepartment() {
            ReportSummary s = aggregator().summary(null, null, 2);

            assertThat(s.getTotalLeavesTaken()).isEqualTo(4.0);
            assertThat(s.getTotalEmployees()).isEqualTo(1);
            assertThat(s.getApprovalRate()).isEqualTo(50.0);
            assertThat(s.getPendingRequests()).isZero();
            assertThat(s.getUtilizationPct()).isEqualTo(50.0);
            assertThat(s.getInsights()).noneMatch(i -> i.contains("highest leave usage"));
        }

        @Test
        void emptyDataHasNullApprovalRateAndZeros() {
            ReportsAggregator empty = new ReportsAggregator(List.of(), List.of(), List.of(), List.of(), List.of(), TODAY);
            ReportSummary s = empty.summary(null, null, null);

            assertThat(s.getTotalLeavesTaken()).isZero();
            assertThat(s.getAvgLeavePerEmployee()).isZero();
            assertThat(s.getApprovalRate()).isNull();
            assertThat(s.getUtilizationPct()).isZero();
            assertThat(s.getInsights()).isEmpty();
        }

        @Test
        void escalationAndLossOfPayInsights() {
            LeaveRequest escalated = requests.get(5);
            escalated.setEscalated(true);
            escalated.setEscalatedAt(LocalDateTime.of(2030, 4, 2, 9, 0));
            requests.get(0).setLopDays(new BigDecimal("1.5"));

            ReportSummary s = aggregator().summary(null, null, null);

            assertThat(s.getInsights())
                    .contains("1 request(s) sat unanswered past the approval SLA and were escalated to HR.")
                    .contains("1.5 day(s) of Loss of Pay were recorded on approved leave.");
        }
    }

    @Test
    void monthlyTrendHasTwelvePointsForApprovedLeaveOnly() {
        List<LeaveTrendPoint> trend = aggregator().monthlyTrend(2030, null);

        assertThat(trend).hasSize(12);
        assertThat(trend.get(0).getMonth()).isEqualTo("Jan");
        assertThat(trend.get(0).getDays()).isEqualTo(5.0);
        assertThat(trend.get(1).getDays()).isEqualTo(1.0);
        assertThat(trend.get(2).getDays()).isEqualTo(4.0);
        assertThat(trend.get(3).getDays()).isZero(); // pending in April ignored
    }

    @Test
    void trendByTypeOnlyIncludesTypesWithData_inCategoryIdOrder() {
        List<LeaveTrendSeries> series = aggregator().trendByType(2030, 1);

        assertThat(series).extracting(LeaveTrendSeries::getCategory).containsExactly("Casual Leave", "Sick Leave");
        assertThat(series.get(0).getColor()).isEqualTo("#2563eb");
        assertThat(series.get(0).getPoints().get(0).getDays()).isEqualTo(5.0);
    }

    @Test
    void distributionSortedByValue() {
        ReportDistributionResponse d = aggregator().distribution(null, null, null);

        assertThat(d.getTotal()).isEqualTo(10.0);
        assertThat(d.getItems()).extracting(ReportDistributionItem::getLabel).containsExactly("Casual Leave", "Sick Leave");
        assertThat(d.getItems()).extracting(ReportDistributionItem::getValue).containsExactly(9.0, 1.0);
    }

    @Test
    void departmentSummaryIsAlphabetical() {
        List<DepartmentSummary> rows = aggregator().departmentSummary(null, null);

        assertThat(rows).extracting(DepartmentSummary::getDepartmentName).containsExactly("Engineering", "Sales");
        DepartmentSummary eng = rows.get(0);
        assertThat(eng.getTotalEmployees()).isEqualTo(2);
        assertThat(eng.getTotalLeaveDays()).isEqualTo(6.0);
        assertThat(eng.getAvgLeaveDaysPerEmployee()).isEqualTo(3.0);
        assertThat(eng.getApprovalRate()).isEqualTo(100.0);
        assertThat(eng.getUtilizationPct()).isEqualTo(25.0);
        assertThat(rows.get(1).getApprovalRate()).isEqualTo(50.0);
    }

    @Test
    void topEmployeesOrderedByDaysThenName() {
        List<TopEmployee> top = aggregator().topEmployees(null, null, 2, null);

        assertThat(top).extracting(TopEmployee::getFullName).containsExactly("Alice", "Carol"); // 4 each, by name
        assertThat(top.get(0).getTotalDaysTaken()).isEqualTo(4.0);
        assertThat(top.get(0).getDepartmentName()).isEqualTo("Engineering");
    }

    @Test
    void topEmployeesNegativeLimitGivesEmpty() {
        assertThat(aggregator().topEmployees(null, null, -1, null)).isEmpty();
    }
}
