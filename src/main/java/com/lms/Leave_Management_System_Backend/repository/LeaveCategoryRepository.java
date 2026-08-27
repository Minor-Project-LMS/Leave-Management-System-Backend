package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveCategoryRepository extends JpaRepository<LeaveCategory, Integer> {

    @EntityGraph(attributePaths = {"department"})
    @Override
    Page<LeaveCategory> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"department"})
    @Override
    Optional<LeaveCategory> findById(Integer id);
}
