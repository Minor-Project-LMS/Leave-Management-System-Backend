package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long> {
    
    List<NotificationQueue> findByUserId(Long userId);
    
    List<NotificationQueue> findByUserIdAndStatus(Long userId, NotificationQueue.NotificationStatus status);
    
    List<NotificationQueue> findByStatus(NotificationQueue.NotificationStatus status);
    
    List<NotificationQueue> findByChannel(NotificationQueue.Channel channel);
    
    List<NotificationQueue> findByRelatedEntityTypeAndRelatedEntityId(String entityType, Long entityId);
}