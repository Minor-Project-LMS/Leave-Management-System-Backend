package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    Page<LeaveRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    Page<LeaveRequest> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    List<LeaveRequest> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    Page<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    List<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    List<LeaveRequest> findByUserIdAndStatusIn(Long userId, List<LeaveRequest.RequestStatus> statuses);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    Page<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    List<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    List<LeaveRequest> findByUserIdAndStatusAndStartDateBetween(Long userId, LeaveRequest.RequestStatus status, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    List<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    Page<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    Page<LeaveRequest> findByCurrentApproverId(Long approverId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover"})
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId OR lr.currentApprover.id = :userId")
    Page<LeaveRequest> findAccessibleByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo", "originalApprover", "hrNotifiedBy"})
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.id IN :requestIds")
    List<LeaveRequest> findByIds(@Param("requestIds") List<Long> requestIds);

    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE lr.status = 'APPROVED' " +
            "AND lr.user.id IN :userIds " +
            "AND lr.startDate <= :endDate " +
            "AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedLeavesForUsers(
            @Param("userIds") List<Long> userIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @EntityGraph(attributePaths = {"user", "category"})
    List<LeaveRequest> findByCompOffRequestId(Long compOffRequestId);

    @Query("SELECT COALESCE(SUM(lr.totalDays), 0) FROM LeaveRequest lr " +
            "WHERE lr.compOffRequest.id = :compOffRequestId " +
            "AND lr.status = 'APPROVED'")
    Double sumDaysClaimedByCompOffRequestId(@Param("compOffRequestId") Long compOffRequestId);

    @Query("SELECT COALESCE(SUM(lr.totalDays), 0) FROM LeaveRequest lr " +
            "WHERE lr.compOffRequest.id = :compOffRequestId " +
            "AND lr.status IN ('PENDING_L1', 'PENDING_L2')")
    Double sumDaysPendingByCompOffRequestId(@Param("compOffRequestId") Long compOffRequestId);

    // Approval SLA escalation: still awaiting a decision, applied before
    // the SLA cutoff, and not already escalated (so a daily run doesn't
    // re-escalate the same request every single day).
    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "originalApprover"})
    List<LeaveRequest> findByStatusInAndAppliedAtBeforeAndEscalatedFalse(
            List<LeaveRequest.RequestStatus> statuses, LocalDateTime cutoff);

    // So a manager's Approval Inbox can still show a request that
    // escalated away from them (see ApprovalsController) — without this,
    // it vanishes from their view entirely the moment it reassigns to HR,
    // since they were never the one who made a decision on it.
    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "originalApprover"})
    List<LeaveRequest> findByOriginalApproverId(Long originalApproverId);

    // Pending requests HR has flagged to this approver — drives the
    // manager dashboard popup. Earliest-starting leave first, since that's
    // the one closest to escalating.
    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "hrNotifiedBy"})
    List<LeaveRequest> findByCurrentApproverIdAndStatusInAndHrNotifiedAtIsNotNullOrderByStartDateAsc(
            Long approverId, List<LeaveRequest.RequestStatus> statuses);
}
