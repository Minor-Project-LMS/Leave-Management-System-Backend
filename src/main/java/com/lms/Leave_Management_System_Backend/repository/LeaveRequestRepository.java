package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    
    Page<LeaveRequest> findByUserId(Long userId, Pageable pageable);
    
    Page<LeaveRequest> findByUserIdAndStatus(Long userId, LeaveRequest.RequestStatus status, Pageable pageable);
    
    Page<LeaveRequest> findByStatus(LeaveRequest.RequestStatus status, Pageable pageable);
    
    List<LeaveRequest> findByUserIdAndStatusAndStartDateBetween(Long userId, LeaveRequest.RequestStatus status, LocalDate startDate, LocalDate endDate);
    
    List<LeaveRequest> findByCurrentApproverIdAndStatus(Long approverId, LeaveRequest.RequestStatus status);
    
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.user.id = :userId OR lr.currentApprover.id = :userId")
    Page<LeaveRequest> findAccessibleByUserId(@Param("userId") Long userId, Pageable pageable);
}
