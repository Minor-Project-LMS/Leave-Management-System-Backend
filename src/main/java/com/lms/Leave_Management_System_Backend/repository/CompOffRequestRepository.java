package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.CompOffRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompOffRequestRepository extends JpaRepository<CompOffRequest, Long> {
    
    @EntityGraph(attributePaths = {"user", "approver"})
    List<CompOffRequest> findByUserId(Long userId);
    
    @EntityGraph(attributePaths = {"user", "approver"})
    List<CompOffRequest> findByApproverId(Long approverId);
    
    @EntityGraph(attributePaths = {"user", "approver"})
    List<CompOffRequest> findByUserIdAndStatus(Long userId, CompOffRequest.RequestStatus status);
    
    // Enhanced methods for API support
    @EntityGraph(attributePaths = {"user", "approver"})
    Page<CompOffRequest> findByUserIdAndStatus(Long userId, CompOffRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "approver"})
    Page<CompOffRequest> findByStatus(CompOffRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "approver"})
    Page<CompOffRequest> findByUserId(Long userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "approver"})
    @Query("SELECT c FROM CompOffRequest c WHERE " +
           "(:userId IS NULL OR c.user.id = :userId) AND " +
           "(:status IS NULL OR c.status = :status)")
    Page<CompOffRequest> findWithFilters(
        @Param("userId") Long userId,
        @Param("status") CompOffRequest.RequestStatus status,
        Pageable pageable);
}