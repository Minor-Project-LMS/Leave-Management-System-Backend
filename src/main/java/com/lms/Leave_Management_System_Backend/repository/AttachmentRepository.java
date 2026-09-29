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

    List<Attachment> findByEntityTypeAndEntityIdAndUploadStatus(
            Attachment.EntityType entityType,
            Long entityId,
            Attachment.UploadStatus uploadStatus
    );

    Optional<Attachment> findByStorageKey(String storageKey);

    @Query("SELECT a FROM Attachment a WHERE a.entityType = :entityType AND a.entityId = :entityId AND a.uploadStatus = 'ACTIVE'")
    List<Attachment> findActiveAttachmentsByEntity(
            @Param("entityType") Attachment.EntityType entityType,
            @Param("entityId") Long entityId
    );

    @Query("SELECT a FROM Attachment a WHERE a.uploadStatus = 'PENDING' AND a.createdAt < :thresholdDate")
    List<Attachment> findStalePendingAttachments(@Param("thresholdDate") java.time.LocalDateTime thresholdDate);

    // For user avatar management - get the current active avatar for a user
    @Query("SELECT a FROM Attachment a WHERE a.entityType = 'USER_AVATAR' AND a.entityId = :userId AND a.uploadStatus = 'ACTIVE'")
    Optional<Attachment> findActiveAvatarByUserId(@Param("userId") Long userId);

    // For retiring old avatars when a new one is set
    @Query("SELECT a FROM Attachment a WHERE a.entityType = 'USER_AVATAR' AND a.entityId = :userId AND a.uploadStatus = 'ACTIVE' AND a.id != :excludeId")
    List<Attachment> findOtherActiveAvatars(@Param("userId") Long userId, @Param("excludeId") Long excludeId);
}
