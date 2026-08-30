package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Attachment;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.AttachmentRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.AuthService;
import com.lms.Leave_Management_System_Backend.service.BlobStorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final AttachmentRepository attachmentRepository;
    private final BlobStorageService blobStorageService;

    public UsersController(UserRepository userRepository, AuthService authService, PasswordEncoder passwordEncoder,
                         AttachmentRepository attachmentRepository, BlobStorageService blobStorageService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.attachmentRepository = attachmentRepository;
        this.blobStorageService = blobStorageService;
    }

    @GetMapping("/me")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        
        UserDto userDto = authService.getUserByEmail(email);
        return ResponseEntity.ok(new ApiResponse<>(true, userDto));
    }

    @PatchMapping("/me")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<UserDto>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // Update self-editable fields per EMP-09 (Personal Information and Emergency Contact cards)
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhone(request.getPhoneNumber());
        }
        if (request.getPersonalEmail() != null) {
            // In real implementation, would update personal email field
        }
        if (request.getEmergencyContactName() != null || request.getEmergencyContactPhone() != null) {
            // In real implementation, would update emergency contact fields
        }

        userRepository.save(user);
        
        UserDto userDto = authService.getUserByEmail(email);
        return ResponseEntity.ok(new ApiResponse<>(true, userDto));
    }

    @PostMapping("/me/password")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        
        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse(
                            "INVALID_PASSWORD",
                            "Current password is incorrect",
                            "/users/me/password"
                    ));
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(new ApiResponse<>(true, null));
    }

    @PostMapping("/me/avatar")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        // Check file size (10 MB limit as per OpenAPI spec)
        long maxSize = 10 * 1024 * 1024; // 10 MB in bytes
        if (file.getSize() > maxSize) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiErrorResponse(
                            "FILE_TOO_LARGE",
                            "File size exceeds the 10 MB limit",
                            "/users/me/avatar"
                    ));
        }

        // Validate file type (images only)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiErrorResponse(
                            "INVALID_FILE_TYPE",
                            "Only image files are allowed",
                            "/users/me/avatar"
                    ));
        }

        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        try {
            // Generate storage key for avatar
            String storageKey = blobStorageService.generateStorageKey(
                    "USER_AVATAR",
                    user.getId(),
                    file.getOriginalFilename()
            );

            // Get bucket for avatars
            String bucket = blobStorageService.getBucketForEntityType("USER_AVATAR");

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
            attachment.setEntityType("USER_AVATAR");
            attachment.setEntityId(user.getId());
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

            attachmentRepository.save(attachment);

            // Update user's avatar URL
            user.setAvatarUrl(blobUrl.toString());
            userRepository.save(user);

            return ResponseEntity.ok(new AvatarResponse(blobUrl.toString()));

        } catch (Exception e) {
            throw new BusinessRuleException("UPLOAD_FAILED", "Failed to upload avatar: " + e.getMessage());
        }
    }
}
