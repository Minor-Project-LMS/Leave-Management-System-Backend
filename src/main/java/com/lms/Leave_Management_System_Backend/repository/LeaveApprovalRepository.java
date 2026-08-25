package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveApproval;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {
    
    @EntityGraph(attributePaths = {"request", "approver", "actingAsDelegateFor"})
    List<LeaveApproval> findByRequestId(Long requestId);
    
    @EntityGraph(attributePaths = {"request", "approver", "actingAsDelegateFor"})
    List<LeaveApproval> findByApproverId(Long approverId);
    
    @EntityGraph(attributePaths = {"request", "approver", "actingAsDelegateFor"})
    List<LeaveApproval> findByRequestIdAndLevel(Long requestId, Integer level);
}