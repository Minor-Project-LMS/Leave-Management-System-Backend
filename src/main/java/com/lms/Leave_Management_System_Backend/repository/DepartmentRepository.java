package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    @EntityGraph(attributePaths = {"departmentHead"})
    @Override
    Page<Department> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"departmentHead"})
    @Override
    Optional<Department> findById(Integer id);

    @EntityGraph(attributePaths = {"departmentHead"})
    Optional<Department> findByDepartmentName(String name);
}
