package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("SELECT a FROM Attachment a WHERE a.entityType = :entityType AND a.entityId = :entityId AND a.status = 'ACTIVE'")
    List<Attachment> findByEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    @Query("SELECT a FROM Attachment a WHERE a.entityType = :entityType AND a.uploadedBy.id = :userId AND a.status = 'ACTIVE'")
    List<Attachment> findByEntityTypeAndUser(@Param("entityType") String entityType, @Param("userId") Long userId);

    @Query("SELECT a FROM Attachment a WHERE a.status = 'PENDING' AND a.uploadedAt < :beforeDate")
    List<Attachment> findExpiredPendingAttachments(@Param("beforeDate") java.time.LocalDateTime beforeDate);

    Optional<Attachment> findByStorageKey(String storageKey);

    @Query("SELECT a FROM Attachment a WHERE a.entityType = 'USER_AVATAR' AND a.uploadedBy.id = :userId AND a.status = 'ACTIVE' ORDER BY a.uploadedAt DESC")
    List<Attachment> findActiveAvatarsByUser(@Param("userId") Long userId);
}
