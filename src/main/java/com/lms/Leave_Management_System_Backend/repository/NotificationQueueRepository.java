package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long> {
    
    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByUserId(Long userId);
    
    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByUserIdAndStatus(Long userId, NotificationQueue.NotificationStatus status);
    
    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByStatus(NotificationQueue.NotificationStatus status);
    
    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByChannel(NotificationQueue.Channel channel);
    
    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByRelatedEntityTypeAndRelatedEntityId(String entityType, Long entityId);
}