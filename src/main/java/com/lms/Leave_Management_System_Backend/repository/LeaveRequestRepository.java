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
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    Page<LeaveRequest> findAll(Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    Page<LeaveRequest> findByUserId(Long userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    List<LeaveRequest> findByUserId(Long userId);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    Page<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    List<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    Page<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    List<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    List<LeaveRequest> findByUserIdAndStatusAndStartDateBetween(Long userId, LeaveRequest.RequestStatus status, LocalDate startDate, LocalDate endDate);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    List<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    Page<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    Page<LeaveRequest> findByCurrentApproverId(Long approverId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category", "currentApprover"})
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId OR lr.currentApprover.id = :userId")
    Page<LeaveRequest> findAccessibleByUserId(@Param("userId") Long userId, Pageable pageable);
}
