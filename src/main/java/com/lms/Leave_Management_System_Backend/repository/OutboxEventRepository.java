package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' AND e.retryCount < 5 ORDER BY e.createdAt ASC")
    List<OutboxEvent> findRetryablePendingEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.retryCount < 5 ORDER BY e.createdAt ASC")
    List<OutboxEvent> findRetryableFailedEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.aggregateType = :aggregateType AND e.aggregateId = :aggregateId ORDER BY e.createdAt DESC")
    List<OutboxEvent> findByAggregate(@Param("aggregateType") String aggregateType, @Param("aggregateId") Long aggregateId);

    @Query("SELECT e FROM OutboxEvent e WHERE e.createdAt < :beforeDate AND e.status = 'PUBLISHED'")
    List<OutboxEvent> findOldPublishedEvents(@Param("beforeDate") LocalDateTime beforeDate);
}
