package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import com.lms.Leave_Management_System_Backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final AttachmentService attachmentService;

    public UsersController(UserRepository userRepository, AuthService authService, PasswordEncoder passwordEncoder, AttachmentService attachmentService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/me")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        userRepository.findByEmailIgnoreCase(email)
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

        // In a real implementation, you would upload to a cloud storage service
        // For now, we'll simulate by setting a placeholder URL
        String avatarUrl = "/uploads/avatars/" + user.getId() + "_" + file.getOriginalFilename();
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return ResponseEntity.ok(new AvatarResponse(avatarUrl));
    }

    // ============================================================
    // AVATAR UPLOAD ENDPOINTS (Direct-to-Storage)
    // ============================================================

    @PostMapping("/me/avatar/init-upload")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AttachmentInitUploadResponse>> initAvatarUpload(
            @Valid @RequestBody AttachmentInitUploadRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        AttachmentInitUploadResponse response = attachmentService.initializeUpload(
                com.lms.Leave_Management_System_Backend.model.Attachment.EntityType.USER_AVATAR,
                user.getId(),
                request,
                user.getId()
        );

        return ResponseEntity.status(201).body(new ApiResponse<>(true, response));
    }

    @PostMapping("/me/avatar/{attachmentId}/confirm")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<ApiResponse<AvatarResponse>> confirmAvatarUpload(
            @PathVariable Long attachmentId,
            @RequestBody(required = false) AttachmentConfirmRequest confirmRequest,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        AttachmentDto attachment = attachmentService.confirmUpload(attachmentId, confirmRequest);

        // Get a fresh download URL for the confirmed attachment
        AttachmentDto attachmentWithUrl = attachmentService.getAttachment(attachmentId);

        // Update user's avatar URL
        user.setAvatarUrl(attachmentWithUrl.getDownloadUrl());
        userRepository.save(user);

        return ResponseEntity.ok(new ApiResponse<>(true, new AvatarResponse(attachmentWithUrl.getDownloadUrl())));
    }
}
