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
}