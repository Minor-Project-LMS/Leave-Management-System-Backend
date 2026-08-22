package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, Long> {
    
    Optional<LeaveLedger> findByUserIdAndCategoryIdAndFiscalYear(Long userId, Integer categoryId, Integer fiscalYear);
    
    List<LeaveLedger> findByUserIdAndFiscalYear(Long userId, Integer fiscalYear);
    
    List<LeaveLedger> findByUserId(Long userId);
    
    List<LeaveLedger> findByCategoryId(Integer categoryId);
    
    // Transaction history queries
    Page<LeaveLedger> findByUserIdAndFiscalYearAndCategoryId(
        Long userId, Integer fiscalYear, Integer categoryId, Pageable pageable);
    
    Page<LeaveLedger> findByUserIdAndFiscalYear(Long userId, Integer fiscalYear, Pageable pageable);
    
    @Query("SELECT l FROM LeaveLedger l WHERE " +
           "(:userId IS NULL OR l.user.id = :userId) AND " +
           "(:fiscalYear IS NULL OR l.fiscalYear = :fiscalYear) AND " +
           "(:categoryId IS NULL OR l.category.id = :categoryId)")
    Page<LeaveLedger> findWithFilters(
        @Param("userId") Long userId,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("categoryId") Integer categoryId,
        Pageable pageable);
    
    @Query("SELECT l FROM LeaveLedger l WHERE " +
           "(:userId IS NULL OR l.user.id = :userId) AND " +
           "(:fiscalYear IS NULL OR l.fiscalYear = :fiscalYear) AND " +
           "(:categoryId IS NULL OR l.category.id = :categoryId) " +
           "ORDER BY l.transactionDate DESC")
    List<LeaveLedger> findTransactionHistory(
        @Param("userId") Long userId,
        @Param("fiscalYear") Integer fiscalYear,
        @Param("categoryId") Integer categoryId);
}