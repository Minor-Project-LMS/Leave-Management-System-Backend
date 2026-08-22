package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.Holiday;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Integer> {
    List<Holiday> findByDateBetween(LocalDate startDate, LocalDate endDate);
    Page<Holiday> findByDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    List<Holiday> findByDateAfter(LocalDate date);
    
    @EntityGraph(attributePaths = {"department"})
    List<Holiday> findByDepartmentId(Integer departmentId);
    
    List<Holiday> findByDepartmentIdIsNull();
    List<Holiday> findByRestricted(boolean restricted);
}
