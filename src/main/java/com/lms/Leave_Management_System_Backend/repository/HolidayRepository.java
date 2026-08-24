package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.Holiday;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Integer> {

    @EntityGraph(attributePaths = {"department"})
    @Override
    Page<Holiday> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"department"})
    @Override
    Optional<Holiday> findById(Integer id);

    @EntityGraph(attributePaths = {"department"})
    List<Holiday> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"department"})
    Page<Holiday> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    @EntityGraph(attributePaths = {"department"})
    List<Holiday> findByHolidayDateAfter(LocalDate date);

    @EntityGraph(attributePaths = {"department"})
    List<Holiday> findByDepartmentId(Integer departmentId);

    @EntityGraph(attributePaths = {"department"})
    List<Holiday> findByDepartmentIdIsNull();

    @EntityGraph(attributePaths = {"department"})
    List<Holiday> findByIsRestricted(boolean restricted);
}
