package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveCategoryRepository extends JpaRepository<LeaveCategory, Integer> {
}
