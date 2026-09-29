package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.CompOffRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompOffRequestRepository extends JpaRepository<CompOffRequest, Long> {

    @EntityGraph(attributePaths = {"user", "issuer"})
    @Override
    Page<CompOffRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "issuer"})
    List<CompOffRequest> findByUserId(Long userId);
    
    @EntityGraph(attributePaths = {"user", "issuer"})
    List<CompOffRequest> findByApproverId(Long approverId);
    
    @EntityGraph(attributePaths = {"user", "issuer"})
    List<CompOffRequest> findByUserIdAndStatus(Long userId, CompOffRequest.RequestStatus status);
    
    // Enhanced methods for API support
    @EntityGraph(attributePaths = {"user", "issuer"})
    Page<CompOffRequest> findByUserIdAndStatus(Long userId, CompOffRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "issuer"})
    Page<CompOffRequest> findByStatus(CompOffRequest.RequestStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "issuer"})
    Page<CompOffRequest> findByUserId(Long userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "issuer"})
    @Query("SELECT c FROM CompOffRequest c WHERE " +
           "(:userId IS NULL OR c.user.id = :userId) AND " +
           "(:status IS NULL OR c.status = :status)")
    Page<CompOffRequest> findWithFilters(
        @Param("userId") Long userId,
        @Param("status") CompOffRequest.RequestStatus status,
        Pageable pageable);

    @EntityGraph(attributePaths = {"user", "issuer"})
    @Query("SELECT c FROM CompOffRequest c WHERE " +
           "(:userId IS NULL OR c.user.id = :userId) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:hasBalance = FALSE OR " +
           "  (c.status = 'APPROVED' AND " +
           "   c.expiryDate >= :today AND " +
           "   c.daysCredited > " +
           "   (SELECT COALESCE(SUM(lr.totalDays), 0) FROM LeaveRequest lr " +
           "    WHERE lr.compOffRequest.id = c.id AND lr.status IN ('PENDING_L1', 'PENDING_L2', 'APPROVED'))))")
    Page<CompOffRequest> findWithFiltersAndBalance(
        @Param("userId") Long userId,
        @Param("status") CompOffRequest.RequestStatus status,
        @Param("hasBalance") Boolean hasBalance,
        @Param("today") LocalDate today,
        Pageable pageable);
}