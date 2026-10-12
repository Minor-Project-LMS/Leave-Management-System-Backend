package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.AttachmentConfirmRequest;
import com.lms.Leave_Management_System_Backend.dto.AttachmentDto;
import com.lms.Leave_Management_System_Backend.dto.AttachmentInitUploadRequest;
import com.lms.Leave_Management_System_Backend.dto.AttachmentInitUploadResponse;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Attachment;
import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.AttachmentRepository;
import com.lms.Leave_Management_System_Backend.repository.AuditTrailRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.TestData.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentServiceTest {

    private AttachmentRepository attachmentRepository;
    private UserRepository userRepository;
    private StorageService storageService;
    private AuditTrailRepository auditTrailRepository;
    private AttachmentService service;
    private User user;

    @BeforeEach
    void setUp() {
        attachmentRepository = mock(AttachmentRepository.class);
        userRepository = mock(UserRepository.class);
        storageService = mock(StorageService.class);
        auditTrailRepository = mock(AuditTrailRepository.class);
        service = new AttachmentService(attachmentRepository, userRepository, storageService, auditTrailRepository);
        user = employee(1L, "emp@lms.com");
        when(storageService.isConfigured()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(42L);
            return a;
        });
    }

    private static AttachmentInitUploadRequest uploadRequest(String name, String type, long size) {
        AttachmentInitUploadRequest r = new AttachmentInitUploadRequest();
        r.setFileName(name);
        r.setContentType(type);
        r.setSizeBytes(size);
        return r;
    }

    private Attachment attachment(Attachment.EntityType type, Attachment.UploadStatus status) {
        Attachment a = new Attachment();
        a.setId(42L);
        a.setEntityType(type);
        a.setEntityId(type == Attachment.EntityType.USER_AVATAR ? 1L : 100L);
        a.setUploadedBy(user);
        a.setFileName("doctor-note.pdf");
        a.setContentType("application/pdf");
        a.setSizeBytes(1000L);
        a.setStorageKey("leave_request/100/abc-doctor-note.pdf");
        a.setUploadStatus(status);
        a.setCreatedAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        return a;
    }

    @Nested
    class InitializeUpload {

        @Test
        @DisplayName("creates a PENDING row with a sanitised storage key and returns a presigned URL")
        void success() {
            when(storageService.generatePresignedPutUrl(anyString(), eq("application/pdf"), eq(2048L)))
                    .thenReturn(new StorageService.PresignedUploadResult("https://s3/put", Map.of("Content-Type", "application/pdf"), 300));

            AttachmentInitUploadResponse response = service.initializeUpload(
                    Attachment.EntityType.LEAVE_REQUEST, 100L, uploadRequest("my note (1).pdf", "application/pdf", 2048), 1L);

            assertThat(response.getAttachmentId()).isEqualTo(42L);
            assertThat(response.getUploadUrl()).isEqualTo("https://s3/put");
            assertThat(response.getExpiresInSeconds()).isEqualTo(300);
            assertThat(response.getStorageKey()).startsWith("leave_request/100/").endsWith("-my_note__1_.pdf");

            ArgumentCaptor<Attachment> saved = ArgumentCaptor.forClass(Attachment.class);
            verify(attachmentRepository).save(saved.capture());
            assertThat(saved.getValue().getUploadStatus()).isEqualTo(Attachment.UploadStatus.PENDING);
            assertThat(saved.getValue().getUploadedBy()).isSameAs(user);
        }

        @Test
        void storageNotConfigured() {
            when(storageService.isConfigured()).thenReturn(false);
            assertThatThrownBy(() -> service.initializeUpload(Attachment.EntityType.LEAVE_REQUEST, 1L,
                    uploadRequest("a.pdf", "application/pdf", 10), 1L))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not properly configured");
        }

        @Test
        void fileOver10MbRejected() {
            assertThatThrownBy(() -> service.initializeUpload(Attachment.EntityType.LEAVE_REQUEST, 1L,
                    uploadRequest("a.pdf", "application/pdf", 10_485_761L), 1L))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("10 MB");
        }

        @Test
        void avatarMustBeAnImage() {
            assertThatThrownBy(() -> service.initializeUpload(Attachment.EntityType.USER_AVATAR, 1L,
                    uploadRequest("a.pdf", "application/pdf", 10), 1L))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Invalid content type for avatar");
        }

        @Test
        void unknownUploader() {
            assertThatThrownBy(() -> service.initializeUpload(Attachment.EntityType.LEAVE_REQUEST, 1L,
                    uploadRequest("a.pdf", "application/pdf", 10), 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ConfirmUpload {

        @Test
        @DisplayName("verifies the object exists, flips to ACTIVE and writes a CREATE audit entry")
        void confirmsLeaveAttachment() {
            Attachment a = attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.PENDING);
            when(attachmentRepository.findById(42L)).thenReturn(Optional.of(a));
            when(storageService.verifyObjectExists(a.getStorageKey())).thenReturn(true);

            AttachmentDto dto = service.confirmUpload(42L, new AttachmentConfirmRequest());

            assertThat(dto.getUploadStatus()).isEqualTo("ACTIVE");
            assertThat(a.getActivatedAt()).isNotNull();
            ArgumentCaptor<AuditTrail> audit = ArgumentCaptor.forClass(AuditTrail.class);
            verify(auditTrailRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(AuditTrail.AuditAction.CREATE);
            assertThat(audit.getValue().getAfterState()).contains("\"attachmentId\":42");
        }

        @Test
        @DisplayName("a new avatar retires the previous active avatars as REPLACED")
        void avatarRetiresOldOnes() {
            Attachment newAvatar = attachment(Attachment.EntityType.USER_AVATAR, Attachment.UploadStatus.PENDING);
            Attachment old = attachment(Attachment.EntityType.USER_AVATAR, Attachment.UploadStatus.ACTIVE);
            old.setId(7L);
            when(attachmentRepository.findById(42L)).thenReturn(Optional.of(newAvatar));
            when(storageService.verifyObjectExists(anyString())).thenReturn(true);
            when(attachmentRepository.findOtherActiveAvatars(1L, 42L)).thenReturn(List.of(old));

            service.confirmUpload(42L, new AttachmentConfirmRequest());

            assertThat(old.getUploadStatus()).isEqualTo(Attachment.UploadStatus.REPLACED);
            ArgumentCaptor<AuditTrail> audit = ArgumentCaptor.forClass(AuditTrail.class);
            verify(auditTrailRepository).save(audit.capture());
            assertThat(audit.getValue().getEntityType()).isEqualTo("USER_AVATAR");
            assertThat(audit.getValue().getAction()).isEqualTo(AuditTrail.AuditAction.UPDATE);
        }

        @Test
        void checksumMismatchOnlyWarns() {
            Attachment a = attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.PENDING);
            when(attachmentRepository.findById(42L)).thenReturn(Optional.of(a));
            when(storageService.verifyObjectExists(anyString())).thenReturn(true);
            when(storageService.getObjectMetadata(anyString())).thenReturn(new StorageService.ObjectMetadata(1000, "etag-x"));
            AttachmentConfirmRequest confirm = new AttachmentConfirmRequest();
            confirm.setChecksumSha256("different");

            assertThat(service.confirmUpload(42L, confirm).getUploadStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void notPending() {
            when(attachmentRepository.findById(42L)).thenReturn(Optional.of(
                    attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.ACTIVE)));
            assertThatThrownBy(() -> service.confirmUpload(42L, new AttachmentConfirmRequest()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not in PENDING");
        }

        @Test
        void objectMissingInStorage() {
            when(attachmentRepository.findById(42L)).thenReturn(Optional.of(
                    attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.PENDING)));
            when(storageService.verifyObjectExists(anyString())).thenReturn(false);
            assertThatThrownBy(() -> service.confirmUpload(42L, new AttachmentConfirmRequest()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Upload not found");
        }

        @Test
        void auditFailureAbortsConfirm() {
            when(attachmentRepository.findById(42L)).thenReturn(Optional.of(
                    attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.PENDING)));
            when(storageService.verifyObjectExists(anyString())).thenReturn(true);
            when(auditTrailRepository.save(any())).thenThrow(new RuntimeException("db"));

            assertThatThrownBy(() -> service.confirmUpload(42L, new AttachmentConfirmRequest()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("audit trail");
        }

        @Test
        void unknownAttachment() {
            when(attachmentRepository.findById(42L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.confirmUpload(42L, new AttachmentConfirmRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void getAttachmentReturnsFreshDownloadUrl() {
        when(attachmentRepository.findById(42L)).thenReturn(Optional.of(
                attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.ACTIVE)));
        when(storageService.generatePresignedGetUrl(anyString())).thenReturn("https://s3/get");

        AttachmentDto dto = service.getAttachment(42L);

        assertThat(dto.getDownloadUrl()).isEqualTo("https://s3/get");
        assertThat(dto.getUploadedBy()).isEqualTo(1L);
    }

    @Test
    void pendingAttachmentIsNotVisible() {
        when(attachmentRepository.findById(42L)).thenReturn(Optional.of(
                attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.PENDING)));
        assertThatThrownBy(() -> service.getAttachment(42L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAttachments() {
        when(attachmentRepository.findActiveAttachmentsByEntity(Attachment.EntityType.LEAVE_REQUEST, 100L))
                .thenReturn(List.of(attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.ACTIVE)));
        when(storageService.generatePresignedGetUrl(anyString())).thenReturn("https://s3/get");

        List<AttachmentDto> list = service.listAttachments(Attachment.EntityType.LEAVE_REQUEST, 100L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getDownloadUrl()).isEqualTo("https://s3/get");
    }

    @Test
    void deleteRemovesFromStorageAndDb() {
        Attachment a = attachment(Attachment.EntityType.LEAVE_REQUEST, Attachment.UploadStatus.ACTIVE);
        when(attachmentRepository.findById(42L)).thenReturn(Optional.of(a));
        when(storageService.deleteObject(anyString())).thenReturn(false); // storage failure only warns

        service.deleteAttachment(42L);

        verify(storageService).deleteObject(a.getStorageKey());
        verify(attachmentRepository).delete(a);
    }

    @Nested
    class ResolveAvatarUrl {

        @Test
        void nullUserGetsDefault() {
            assertThat(service.resolveAvatarUrl(null)).isEqualTo("/assets/images/default-avatar.png");
        }

        @Test
        void activeAttachmentWins() {
            when(attachmentRepository.findActiveAvatarByUserId(1L)).thenReturn(Optional.of(
                    attachment(Attachment.EntityType.USER_AVATAR, Attachment.UploadStatus.ACTIVE)));
            when(storageService.generatePresignedGetUrl(anyString())).thenReturn("https://s3/avatar");

            assertThat(service.resolveAvatarUrl(1L)).isEqualTo("https://s3/avatar");
        }

        @Test
        void signingFailureFallsBackToDefault() {
            when(attachmentRepository.findActiveAvatarByUserId(1L)).thenReturn(Optional.of(
                    attachment(Attachment.EntityType.USER_AVATAR, Attachment.UploadStatus.ACTIVE)));
            when(storageService.generatePresignedGetUrl(anyString())).thenThrow(new IllegalStateException("no s3"));

            assertThat(service.resolveAvatarUrl(1L)).isEqualTo("/assets/images/default-avatar.png");
        }

        @Test
        void fallsBackToUserAvatarUrlColumn() {
            when(attachmentRepository.findActiveAvatarByUserId(1L)).thenReturn(Optional.empty());
            user.setAvatarUrl("https://legacy/avatar.png");

            assertThat(service.resolveAvatarUrl(1L)).isEqualTo("https://legacy/avatar.png");
        }

        @Test
        void defaultWhenNothingStored() {
            when(attachmentRepository.findActiveAvatarByUserId(1L)).thenReturn(Optional.empty());
            user.setAvatarUrl("   ");

            assertThat(service.resolveAvatarUrl(1L)).isEqualTo("/assets/images/default-avatar.png");
        }
    }
}
