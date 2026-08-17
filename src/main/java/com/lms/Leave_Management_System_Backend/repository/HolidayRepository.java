package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Integer> {
    List<Holiday> findByDateBetween(LocalDate startDate, LocalDate endDate);
    List<Holiday> findByDateAfter(LocalDate date);
}
