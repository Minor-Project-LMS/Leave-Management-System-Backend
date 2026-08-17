package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmployeeCode(String employeeCode);

    List<User> findByDepartmentId(Integer departmentId);

    List<User> findByReportsToId(Long managerId);

    @Query("SELECT u FROM User u WHERE u.employmentStatus = 'ACTIVE'")
    List<User> findActiveUsers();

    @Query("SELECT u FROM User u WHERE u.employmentStatus = 'ACTIVE' AND u.department.id = :departmentId")
    List<User> findActiveUsersByDepartment(@Param("departmentId") Integer departmentId);
}