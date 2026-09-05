package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Attachment;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.AttachmentRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final long MAX_FILE_SIZE_BYTES = 10_485_760; // 10 MB

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            UserRepository userRepository,
            StorageService storageService) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    /**
     * Initialize a direct-to-storage upload
     * Creates an attachment row as PENDING and returns a pre-signed PUT URL
     */
    @Transactional
    public AttachmentInitUploadResponse initializeUpload(
            Attachment.EntityType entityType,
            Long entityId,
            AttachmentInitUploadRequest request,
            Long uploadedByUserId) {

        // Validate storage service is configured
        if (!storageService.isConfigured()) {
            throw new BusinessRuleException("Storage service is not properly configured. File uploads are disabled.");
        }

        // Validate file size
        if (request.getSizeBytes() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleException("File size exceeds maximum allowed size of 10 MB");
        }

        // Validate content type for avatar uploads
        if (entityType == Attachment.EntityType.USER_AVATAR) {
            if (!isValidImageContentType(request.getContentType())) {
                throw new BusinessRuleException("Invalid content type for avatar. Only image/jpeg, image/png, image/gif, and image/webp are allowed.");
            }
        }

        // Get the uploading user
        User uploadedBy = userRepository.findById(uploadedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", uploadedByUserId));

        // Generate unique storage key
        String storageKey = generateStorageKey(entityType, entityId, request.getFileName());

        // Create attachment record in PENDING status
        Attachment attachment = new Attachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setUploadedBy(uploadedBy);
        attachment.setFileName(request.getFileName());
        attachment.setContentType(request.getContentType());
        attachment.setSizeBytes(request.getSizeBytes());
        attachment.setStorageKey(storageKey);
        attachment.setChecksumSha256(request.getChecksumSha256());
        attachment.setUploadStatus(Attachment.UploadStatus.PENDING);

        Attachment savedAttachment = attachmentRepository.save(attachment);

        // Generate pre-signed PUT URL
        StorageService.PresignedUploadResult presignedResult = 
                storageService.generatePresignedPutUrl(storageKey, request.getContentType(), request.getSizeBytes());

        // Build response
        AttachmentInitUploadResponse response = new AttachmentInitUploadResponse(
                savedAttachment.getId(),
                presignedResult.getUploadUrl(),
                storageKey
        );
        response.setRequiredHeaders(presignedResult.getRequiredHeaders());
        response.setExpiresInSeconds(presignedResult.getExpiresInSeconds());

        log.info("Initialized upload for attachment ID: {}, entity: {}:{}, storageKey: {}", 
                savedAttachment.getId(), entityType, entityId, storageKey);

        return response;
    }

    /**
     * Confirm a direct-to-storage upload completed
     * Verifies the object exists in storage and flips status to ACTIVE
     */
    @Transactional
    public AttachmentDto confirmUpload(
            Long attachmentId,
            AttachmentConfirmRequest confirmRequest) {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        // Verify the attachment is still in PENDING status
        if (attachment.getUploadStatus() != Attachment.UploadStatus.PENDING) {
            throw new BusinessRuleException("Attachment is not in PENDING status. Current status: " + attachment.getUploadStatus());
        }

        // Verify the object exists in storage
        if (!storageService.verifyObjectExists(attachment.getStorageKey())) {
            throw new BusinessRuleException("Upload not found in storage. The file may not have been uploaded successfully.");
        }

        // Optional: Verify checksum if provided
        if (confirmRequest.getChecksumSha256() != null && !confirmRequest.getChecksumSha256().isEmpty()) {
            StorageService.ObjectMetadata metadata = storageService.getObjectMetadata(attachment.getStorageKey());
            if (metadata != null) {
                // Note: S3 ETag is not always a SHA256 hash, it depends on upload method
                // For single-part uploads, ETag is MD5. For multipart, it's different.
                // We'll log a warning if checksums don't match but won't fail for now
                if (!confirmRequest.getChecksumSha256().equals(metadata.getETag())) {
                    log.warn("Checksum mismatch for attachment ID: {}. Provided: {}, Storage ETag: {}", 
                            attachmentId, confirmRequest.getChecksumSha256(), metadata.getETag());
                    // Uncomment to enforce strict checksum verification:
                    // throw new BusinessRuleException("Checksum verification failed");
                }
            }
        }

        // For user avatars, retire old active avatars
        if (attachment.getEntityType() == Attachment.EntityType.USER_AVATAR) {
            retireOldAvatars(attachment.getEntityId(), attachmentId);
        }

        // Flip status to ACTIVE
        attachment.setUploadStatus(Attachment.UploadStatus.ACTIVE);
        attachment.setActivatedAt(LocalDateTime.now());
        Attachment savedAttachment = attachmentRepository.save(attachment);

        log.info("Confirmed upload for attachment ID: {}, status set to ACTIVE", savedAttachment.getId());

        return toAttachmentDto(savedAttachment);
    }

    /**
     * Get attachment metadata with a fresh pre-signed download URL
     */
    @Transactional(readOnly = true)
    public AttachmentDto getAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        // Only return ACTIVE attachments
        if (attachment.getUploadStatus() != Attachment.UploadStatus.ACTIVE) {
            throw new ResourceNotFoundException("Attachment", attachmentId);
        }

        // Generate fresh pre-signed download URL
        String downloadUrl = storageService.generatePresignedGetUrl(attachment.getStorageKey());

        AttachmentDto dto = toAttachmentDto(attachment);
        dto.setDownloadUrl(downloadUrl);

        return dto;
    }

    /**
     * List ACTIVE attachments for an entity
     */
    @Transactional(readOnly = true)
    public List<AttachmentDto> listAttachments(Attachment.EntityType entityType, Long entityId) {
        List<Attachment> attachments = attachmentRepository.findActiveAttachmentsByEntity(entityType, entityId);

        return attachments.stream()
                .map(attachment -> {
                    String downloadUrl = storageService.generatePresignedGetUrl(attachment.getStorageKey());
                    AttachmentDto dto = toAttachmentDto(attachment);
                    dto.setDownloadUrl(downloadUrl);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Delete an attachment (both from storage and database)
     */
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        // Delete from storage
        if (storageService.isConfigured()) {
            boolean deleted = storageService.deleteObject(attachment.getStorageKey());
            if (!deleted) {
                log.warn("Failed to delete object from storage: {}", attachment.getStorageKey());
            }
        }

        // Delete from database
        attachmentRepository.delete(attachment);

        log.info("Deleted attachment ID: {}, storageKey: {}", attachmentId, attachment.getStorageKey());
    }

    /**
     * Helper method to generate unique storage key
     */
    private String generateStorageKey(Attachment.EntityType entityType, Long entityId, String fileName) {
        String entityTypePath = entityType.name().toLowerCase();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("%s/%d/%s-%s", entityTypePath, entityId, uuid, sanitizedFileName);
    }

    /**
     * Helper method to validate image content types
     */
    private boolean isValidImageContentType(String contentType) {
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/gif") ||
                contentType.equals("image/webp")
        );
    }

    /**
     * Helper method to retire old avatars when a new one is set
     */
    private void retireOldAvatars(Long userId, Long newAttachmentId) {
        List<Attachment> oldAvatars = attachmentRepository.findOtherActiveAvatars(userId, newAttachmentId);
        
        for (Attachment oldAvatar : oldAvatars) {
            // We could either delete them completely or mark them as superseded
            // For now, let's delete them from storage and database
            if (storageService.isConfigured()) {
                storageService.deleteObject(oldAvatar.getStorageKey());
            }
            attachmentRepository.delete(oldAvatar);
            log.info("Retired old avatar attachment ID: {} for user ID: {}", oldAvatar.getId(), userId);
        }
    }

    /**
     * Convert Attachment entity to DTO
     */
    private AttachmentDto toAttachmentDto(Attachment attachment) {
        AttachmentDto dto = new AttachmentDto();
        dto.setId(attachment.getId().intValue());
        dto.setFileName(attachment.getFileName());
        dto.setContentType(attachment.getContentType());
        dto.setSizeBytes(attachment.getSizeBytes());
        dto.setUploadedBy(attachment.getUploadedBy().getId());
        dto.setUploadedAt(attachment.getCreatedAt());
        dto.setUploadStatus(attachment.getUploadStatus().name());
        // downloadUrl is set separately
        return dto;
    }
}
