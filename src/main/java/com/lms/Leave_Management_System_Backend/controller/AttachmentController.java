package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.AttachmentDto;
import com.lms.Leave_Management_System_Backend.dto.AttachmentUploadUrl;
import com.lms.Leave_Management_System_Backend.dto.AttachmentUploadUrlInput;
import com.lms.Leave_Management_System_Backend.dto.ApiResponse;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Attachment;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.AttachmentRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.BlobStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final BlobStorageService blobStorageService;

    public AttachmentController(AttachmentRepository attachmentRepository,
                              UserRepository userRepository,
                              BlobStorageService blobStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.blobStorageService = blobStorageService;
    }

    @PostMapping("/upload-url")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<AttachmentUploadUrl> generateUploadUrl(
            @RequestBody AttachmentUploadUrlInput request,
            Authentication authentication) {

        // Validate file size
        if (request.getSizeBytes() > MAX_FILE_SIZE) {
            throw new BusinessRuleException("FILE_TOO_LARGE", "File size exceeds maximum limit of 10 MB");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Generate storage key
        String storageKey = blobStorageService.generateStorageKey(
                request.getEntityType(),
                request.getEntityId(),
                request.getFileName()
        );

        // Get bucket for entity type
        String bucket = blobStorageService.getBucketForEntityType(request.getEntityType());

        // Create PENDING attachment
        Attachment attachment = new Attachment();
        attachment.setEntityType(request.getEntityType());
        attachment.setEntityId(request.getEntityId());
        attachment.setFileName(request.getFileName());
        attachment.setContentType(request.getContentType());
        attachment.setSizeBytes(request.getSizeBytes());
        attachment.setStorageProvider(Attachment.StorageProvider.S3);
        attachment.setStorageBucket(bucket);
        attachment.setStorageKey(storageKey);
        attachment.setStatus(Attachment.AttachmentStatus.PENDING);
        attachment.setUploadedBy(user);

        Attachment saved = attachmentRepository.save(attachment);

        // Generate presigned PUT URL (15 minutes expiry)
        Duration expiry = Duration.ofMinutes(15);
        URL uploadUrl = blobStorageService.generatePresignedPutUrl(storageKey, request.getContentType(), expiry);

        AttachmentUploadUrl response = new AttachmentUploadUrl();
        response.setAttachmentId(saved.getId());
        response.setUploadUrl(uploadUrl);
        response.setStorageKey(storageKey);
        response.setExpiresAt(LocalDateTime.now().plus(expiry));

        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/{attachmentId}/confirm")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<AttachmentDto> confirmUpload(
            @PathVariable Long attachmentId,
            @RequestBody(required = false) Map<String, Long> requestBody,
            Authentication authentication) {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        // Verify user can confirm this attachment
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!attachment.getUploadedBy().getId().equals(currentUser.getId())) {
            throw new BusinessRuleException("UNAUTHORIZED", "You can only confirm your own uploads");
        }

        if (attachment.getStatus() != Attachment.AttachmentStatus.PENDING) {
            throw new BusinessRuleException("INVALID_STATUS", "Attachment is not in PENDING status");
        }

        try {
            // Verify object exists in blob storage
            BlobStorageService.ObjectMetadata metadata = blobStorageService.headObject(attachment.getStorageKey());

            // Generate blob URL (presigned GET URL with longer expiry)
            Duration expiry = Duration.ofHours(24);
            URL blobUrl = blobStorageService.generatePresignedGetUrl(attachment.getStorageKey(), expiry);

            // Update attachment
            attachment.setStatus(Attachment.AttachmentStatus.ACTIVE);
            attachment.setBlobUrl(blobUrl.toString());
            attachment.setBlobUrlExpiresAt(LocalDateTime.now().plus(expiry));
            
            // Update entity ID if provided
            if (requestBody != null && requestBody.containsKey("entityId")) {
                attachment.setEntityId(requestBody.get("entityId"));
            }

            Attachment saved = attachmentRepository.save(attachment);

            return ResponseEntity.ok(toAttachmentDto(saved));

        } catch (Exception e) {
            // Object not found in blob storage
            throw new BusinessRuleException("UPLOAD_FAILED", "Upload verification failed: " + e.getMessage());
        }
    }

    @PostMapping("/leave-requests/{requestId}/attachments")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<AttachmentDto> uploadAttachmentMultipart(
            @PathVariable Long requestId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleException("FILE_TOO_LARGE", "File size exceeds maximum limit of 10 MB");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        try {
            // Generate storage key
            String storageKey = blobStorageService.generateStorageKey(
                    "LEAVE_REQUEST",
                    requestId,
                    file.getOriginalFilename()
            );

            // Get bucket
            String bucket = blobStorageService.getBucketForEntityType("LEAVE_REQUEST");

            // Stream upload to blob storage
            try (InputStream inputStream = file.getInputStream()) {
                blobStorageService.putObject(
                        storageKey,
                        inputStream,
                        file.getContentType(),
                        file.getSize()
                );
            }

            // Generate blob URL
            Duration expiry = Duration.ofHours(24);
            URL blobUrl = blobStorageService.generatePresignedGetUrl(storageKey, expiry);

            // Create ACTIVE attachment
            Attachment attachment = new Attachment();
            attachment.setEntityType("LEAVE_REQUEST");
            attachment.setEntityId(requestId);
            attachment.setFileName(file.getOriginalFilename());
            attachment.setContentType(file.getContentType());
            attachment.setSizeBytes(file.getSize());
            attachment.setStorageProvider(Attachment.StorageProvider.S3);
            attachment.setStorageBucket(bucket);
            attachment.setStorageKey(storageKey);
            attachment.setBlobUrl(blobUrl.toString());
            attachment.setBlobUrlExpiresAt(LocalDateTime.now().plus(expiry));
            attachment.setStatus(Attachment.AttachmentStatus.ACTIVE);
            attachment.setUploadedBy(user);

            Attachment saved = attachmentRepository.save(attachment);

            return ResponseEntity.status(201).body(toAttachmentDto(saved));

        } catch (Exception e) {
            throw new BusinessRuleException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }

    @GetMapping("/leave-requests/{requestId}/attachments/{attachmentId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<?> downloadAttachment(
            @PathVariable Long requestId,
            @PathVariable Long attachmentId,
            Authentication authentication) {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        // Verify attachment belongs to the specified request
        if (!"LEAVE_REQUEST".equals(attachment.getEntityType()) || 
            !attachment.getEntityId().equals(requestId)) {
            throw new ResourceNotFoundException("Attachment", attachmentId);
        }

        try {
            // Generate fresh presigned GET URL
            Duration expiry = Duration.ofMinutes(15);
            URL downloadUrl = blobStorageService.generatePresignedGetUrl(attachment.getStorageKey(), expiry);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(downloadUrl.toURI());

            return ResponseEntity.status(HttpStatus.FOUND)
                    .headers(headers)
                    .build();

        } catch (Exception e) {
            throw new BusinessRuleException("DOWNLOAD_FAILED", "Failed to generate download URL: " + e.getMessage());
        }
    }

    private AttachmentDto toAttachmentDto(Attachment attachment) {
        AttachmentDto dto = new AttachmentDto();
        dto.setId(attachment.getId().intValue());
        dto.setEntityType(attachment.getEntityType());
        dto.setEntityId(attachment.getEntityId() != null ? attachment.getEntityId().intValue() : null);
        dto.setFileName(attachment.getFileName());
        dto.setContentType(attachment.getContentType());
        dto.setSizeBytes(attachment.getSizeBytes());
        dto.setStorageProvider(attachment.getStorageProvider().name());
        dto.setStatus(attachment.getStatus().name());
        dto.setBlobUrl(attachment.getBlobUrl());
        dto.setDownloadUrl(attachment.getBlobUrl()); // For now, same as blobUrl
        dto.setUploadedBy(attachment.getUploadedBy().getId().intValue());
        dto.setUploadedAt(attachment.getUploadedAt());
        return dto;
    }
}
