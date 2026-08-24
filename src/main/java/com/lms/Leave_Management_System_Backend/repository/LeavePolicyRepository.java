package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeavePolicy;
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
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Integer> {

    @EntityGraph(attributePaths = {"category", "department"})
    @Override
    Page<LeavePolicy> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "department"})
    @Override
    Optional<LeavePolicy> findById(Integer id);

    @EntityGraph(attributePaths = {"category", "department"})
    List<LeavePolicy> findByCategoryId(Integer categoryId);

    @EntityGraph(attributePaths = {"category", "department"})
    List<LeavePolicy> findByDepartmentId(Integer departmentId);

    @EntityGraph(attributePaths = {"category", "department"})
    Optional<LeavePolicy> findByCategoryIdAndDepartmentId(Integer categoryId, Integer departmentId);

    @EntityGraph(attributePaths = {"category", "department"})
    List<LeavePolicy> findByCategoryIdAndDepartmentIdIsNull(Integer categoryId);

    // Enhanced methods for API support (without status since model doesn't have it)
    @EntityGraph(attributePaths = {"category", "department"})
    @Query("SELECT p FROM LeavePolicy p WHERE " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:departmentId IS NULL OR p.department.id = :departmentId)")
    Page<LeavePolicy> findWithFilters(
        @Param("categoryId") Integer categoryId,
        @Param("departmentId") Integer departmentId,
        Pageable pageable);
}