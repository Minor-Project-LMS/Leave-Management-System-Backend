package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Override
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Override
    Optional<User> findById(Long id);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    Optional<User> findByEmployeeCode(String employeeCode);

    // Used when generating the next human-readable employee/manager code.
    List<User> findByEmployeeCodeStartingWith(String prefix);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    List<User> findByDepartmentId(Integer departmentId);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    List<User> findByReportsToId(Long managerId);

    @Query("SELECT u FROM User u WHERE u.employmentStatus = 'ACTIVE'")
    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    List<User> findActiveUsers();

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Query("SELECT u FROM User u WHERE u.employmentStatus = 'ACTIVE' AND u.department.id = :departmentId")
    List<User> findActiveUsersByDepartment(@Param("departmentId") Integer departmentId);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    List<User> findByEmploymentStatus(User.EmploymentStatus employmentStatus);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    Optional<User> findWithReportsToById(Long id);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Query("SELECT u FROM User u WHERE u.role.roleCode = :roleCode AND u.employmentStatus = 'ACTIVE'")
    Optional<User> findFirstByRole_RoleCode(@Param("roleCode") String roleCode);

    // Used for the Delegation "Delegate To" picker — a manager delegates
    // approval authority to HR (mirrors the same escalation target used
    // when a leave request auto-routes to HR at PENDING_L2, see
    // LeaveRequestsController#findFirstByRole_RoleCode), not to their own
    // direct reports.
    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Query("SELECT u FROM User u WHERE u.role.roleCode IN :roleCodes AND u.employmentStatus = 'ACTIVE' ORDER BY u.fullName ASC")
    List<User> findActiveUsersByRoleCodes(@Param("roleCodes") List<String> roleCodes);

    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Query("SELECT u FROM User u WHERE " +
            "(:isManager = false OR u.reportsTo.id = :managerId) AND " +
            "(:departmentId IS NULL OR u.department.id = :departmentId) AND " +
            "(CAST(:q AS string) IS NULL OR " +
            " LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            " LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            " LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
    Page<User> findTeamMembers(
            @Param("isManager") boolean isManager,
            @Param("managerId") Long managerId,
            @Param("departmentId") Integer departmentId,
            @Param("q") String q,
            Pageable pageable);

    // Employee Management directory (HR-01) — excludes HR_ADMIN so HR staff
    // (e.g. Anita) don't show up mixed in with regular employees/managers,
    // and does the department/designation/status/search filtering that
    // EmployeesController previously stubbed out (every branch fell back to
    // an unfiltered findAll(pageable)).
    @EntityGraph(attributePaths = {"role", "department", "reportsTo"})
    @Query("SELECT u FROM User u WHERE " +
            "u.role.roleCode <> 'HR_ADMIN' AND " +
            "(:departmentId IS NULL OR u.department.id = :departmentId) AND " +
            "(CAST(:designation AS string) IS NULL OR LOWER(u.designation) = LOWER(CAST(:designation AS string))) AND " +
            "(:status IS NULL OR u.employmentStatus = :status) AND " +
            "(CAST(:q AS string) IS NULL OR " +
            " LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            " LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR " +
            " LOWER(u.employeeCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))")
    Page<User> findEmployeeDirectory(
            @Param("departmentId") Integer departmentId,
            @Param("designation") String designation,
            @Param("status") User.EmploymentStatus status,
            @Param("q") String q,
            Pageable pageable);
}