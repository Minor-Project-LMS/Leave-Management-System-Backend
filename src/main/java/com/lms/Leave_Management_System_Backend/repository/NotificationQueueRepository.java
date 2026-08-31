package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long>, JpaSpecificationExecutor<NotificationQueue> {

    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByUserId(Long userId);

    List<NotificationQueue> findByStatus(NotificationQueue.NotificationStatus status);


    @EntityGraph(attributePaths = {"user"})
    List<NotificationQueue> findByUserIdAndIsReadFalse(Long userId);

    long countByUserIdAndChannelAndIsReadFalse(Long userId, NotificationQueue.Channel channel);

    @EntityGraph(attributePaths = {"user"})
    Page<NotificationQueue> findByUserIdAndChannelAndIsReadFalse(Long userId, NotificationQueue.Channel channel, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<NotificationQueue> findByUserIdAndChannelAndRelatedEntityTypeIn(
            Long userId, NotificationQueue.Channel channel, Collection<String> relatedEntityTypes, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<NotificationQueue> findByUserIdAndChannel(Long userId, NotificationQueue.Channel channel, Pageable pageable);
}