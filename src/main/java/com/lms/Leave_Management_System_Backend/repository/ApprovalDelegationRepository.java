package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.ApprovalDelegation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Integer> {
    
    @EntityGraph(attributePaths = {"delegator", "delegate"})
    Page<ApprovalDelegation> findByDelegatorId(Long delegatorId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"delegator", "delegate"})
    List<ApprovalDelegation> findByDelegateId(Long delegateId);
    
    @EntityGraph(attributePaths = {"delegator", "delegate"})
    @Query("SELECT d FROM ApprovalDelegation d WHERE d.delegator.id = :delegatorId " +
           "AND d.startDate <= :date AND d.endDate >= :date AND d.active = true")
    List<ApprovalDelegation> findActiveDelegationsForDelegatorOnDate(
            @Param("delegatorId") Long delegatorId, 
            @Param("date") LocalDate date);
    
    @EntityGraph(attributePaths = {"delegator", "delegate"})
    @Query("SELECT d FROM ApprovalDelegation d WHERE d.delegate.id = :delegateId " +
           "AND d.startDate <= :date AND d.endDate >= :date AND d.active = true")
    List<ApprovalDelegation> findActiveDelegationsForDelegateOnDate(
            @Param("delegateId") Long delegateId, 
            @Param("date") LocalDate date);
    
    @EntityGraph(attributePaths = {"delegator", "delegate"})
    @Query("SELECT d FROM ApprovalDelegation d WHERE d.delegator.id = :delegatorId " +
           "AND d.startDate <= :endDate AND d.endDate >= :startDate AND d.active = true")
    Optional<ApprovalDelegation> findOverlappingDelegations(
            @Param("delegatorId") Long delegatorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}