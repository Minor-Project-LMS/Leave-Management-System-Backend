package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.dto.TeamLeaveSummary;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, Long> {
    
    @EntityGraph(attributePaths = {"user", "category"})
    Optional<LeaveLedger> findByUserIdAndCategoryIdAndFiscalYear(Long userId, Integer categoryId, Integer fiscalYear);
    
    @EntityGraph(attributePaths = {"user", "category"})
    List<LeaveLedger> findByUserIdAndFiscalYear(Long userId, Integer fiscalYear);
    
    @EntityGraph(attributePaths = {"user", "category"})
    List<LeaveLedger> findByUserId(Long userId);
    
    @EntityGraph(attributePaths = {"user", "category"})
    List<LeaveLedger> findByCategoryId(Integer categoryId);
    
    // Transaction history queries
    @EntityGraph(attributePaths = {"user", "category"})
    Page<LeaveLedger> findByUserIdAndFiscalYearAndCategoryId(
        Long userId, Integer fiscalYear, Integer categoryId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category"})
    Page<LeaveLedger> findByUserIdAndFiscalYear(Long userId, Integer fiscalYear, Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT l FROM LeaveLedger l WHERE " +
           "(:userId IS NULL OR l.user.id = :userId) AND " +
           "(:fiscalYear IS NULL OR l.fiscalYear = :fiscalYear) AND " +
           "(:categoryId IS NULL OR l.category.id = :categoryId)")
    Page<LeaveLedger> findWithFilters(
        @Param("userId") Long userId,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("categoryId") Integer categoryId,
        Pageable pageable);
    
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT l FROM LeaveLedger l WHERE " +
           "(:userId IS NULL OR l.user.id = :userId) AND " +
           "(:fiscalYear IS NULL OR l.fiscalYear = :fiscalYear) AND " +
           "(:categoryId IS NULL OR l.category.id = :categoryId) " +
           "ORDER BY l.transactionDate DESC")
    List<LeaveLedger> findTransactionHistory(
        @Param("userId") Long userId,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("categoryId") Integer categoryId);

    @Query("SELECT new com.lms.Leave_Management_System_Backend.dto.TeamLeaveSummary(" +
            "  c.id, " +
            "  c.categoryName, " +
            "  CAST(SUM(ll.used) AS double) " +
            ") " +
            "FROM LeaveLedger ll " +
            "JOIN ll.category c " +
            "WHERE ll.user.id IN :userIds " +
            "AND ll.fiscalYear = :year " +
            "GROUP BY c.id, c.categoryName")
    List<TeamLeaveSummary> findTeamLeaveSummary(
            @Param("userIds") List<Long> userIds,
            @Param("year") int year);
}