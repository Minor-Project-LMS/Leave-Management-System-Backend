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

    // Legacy multipart avatar upload removed - all avatar uploads now use pre-signed URL flow
    // through /me/avatar/init-upload and /me/avatar/{attachmentId}/confirm endpoints

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
