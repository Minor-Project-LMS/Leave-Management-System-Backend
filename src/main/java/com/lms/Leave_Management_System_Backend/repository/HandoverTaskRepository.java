package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.HandoverTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoverTaskRepository extends JpaRepository<HandoverTask, Long> {

    List<HandoverTask> findByLeaveRequestIdOrderByIdAsc(Long leaveRequestId);
}
