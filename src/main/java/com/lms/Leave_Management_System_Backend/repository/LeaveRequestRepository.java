package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    Page<LeaveRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    Page<LeaveRequest> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    List<LeaveRequest> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    Page<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    List<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    Page<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    List<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    List<LeaveRequest> findByUserIdAndStatusAndStartDateBetween(Long userId, LeaveRequest.RequestStatus status, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    List<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    Page<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    Page<LeaveRequest> findByCurrentApproverId(Long approverId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId OR lr.currentApprover.id = :userId")
    Page<LeaveRequest> findAccessibleByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category", "currentApprover", "handoverTo"})
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
}
