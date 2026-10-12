package com.lms.Leave_Management_System_Backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceTest {

    @Nested
    @DisplayName("when S3 credentials are not configured")
    class Unconfigured {

        private StorageService service;

        @BeforeEach
        void setUp() {
            service = new StorageService();
            ReflectionTestUtils.setField(service, "storageEndpoint", "");
            ReflectionTestUtils.setField(service, "accessKey", "");
            ReflectionTestUtils.setField(service, "secretKey", "");
            service.initialize();
        }

        @Test
        void reportsNotConfiguredAndRefusesOperations() {
            assertThat(service.isConfigured()).isFalse();
            assertThatThrownBy(() -> service.generatePresignedPutUrl("k", "image/png", 1)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.generatePresignedGetUrl("k")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.verifyObjectExists("k")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.getObjectMetadata("k")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.deleteObject("k")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("initialize() builds real (offline) clients when endpoint and keys are present")
    void initializeWithConfig() {
        StorageService service = new StorageService();
        ReflectionTestUtils.setField(service, "storageEndpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(service, "accessKey", "key");
        ReflectionTestUtils.setField(service, "secretKey", "secret");
        ReflectionTestUtils.setField(service, "region", "us-east-1");
        ReflectionTestUtils.setField(service, "bucketName", "lms-attachments");

        service.initialize();

        assertThat(service.isConfigured()).isTrue();
    }

    @Nested
    @DisplayName("with mocked S3 clients")
    class Configured {

        private StorageService service;
        private S3Client s3;
        private S3Presigner presigner;

        @BeforeEach
        void setUp() {
            service = new StorageService();
            s3 = mock(S3Client.class);
            presigner = mock(S3Presigner.class);
            ReflectionTestUtils.setField(service, "s3Client", s3);
            ReflectionTestUtils.setField(service, "s3Presigner", presigner);
            ReflectionTestUtils.setField(service, "bucketName", "lms-attachments");
        }

        @Test
        void presignedPutUrl() throws Exception {
            PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
            when(presigned.url()).thenReturn(new URL("https://s3.example/put"));
            when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

            StorageService.PresignedUploadResult result = service.generatePresignedPutUrl("k", "image/png", 100);

            assertThat(result.getUploadUrl()).isEqualTo("https://s3.example/put");
            assertThat(result.getRequiredHeaders()).containsEntry("Content-Type", "image/png");
            assertThat(result.getExpiresInSeconds()).isEqualTo(300);
        }

        @Test
        void presignedPutRejectsLargeFilesAndOddImageTypes() {
            assertThatThrownBy(() -> service.generatePresignedPutUrl("k", "application/pdf", 10_485_761L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.generatePresignedPutUrl("k", "image/bmp", 100))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image/bmp");
        }

        @Test
        void presignerFailureIsWrapped() {
            when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenThrow(new RuntimeException("boom"));
            assertThatThrownBy(() -> service.generatePresignedPutUrl("k", "application/pdf", 100))
                    .isInstanceOf(RuntimeException.class).hasMessage("Failed to generate upload URL");
        }

        @Test
        void presignedGetUrl() throws Exception {
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(new URL("https://s3.example/get"));
            when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

            assertThat(service.generatePresignedGetUrl("k")).isEqualTo("https://s3.example/get");
        }

        @Test
        void verifyObjectExists() {
            when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
            assertThat(service.verifyObjectExists("k")).isTrue();
        }

        @Test
        void verifyObjectMissing() {
            when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
            assertThat(service.verifyObjectExists("k")).isFalse();
            assertThat(service.getObjectMetadata("k")).isNull();
        }

        @Test
        void verifyObjectOtherError() {
            when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(new RuntimeException("network"));
            assertThat(service.verifyObjectExists("k")).isFalse();
            assertThat(service.getObjectMetadata("k")).isNull();
        }

        @Test
        void objectMetadata() {
            when(s3.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().contentLength(123L).eTag("\"abc\"").build());

            StorageService.ObjectMetadata meta = service.getObjectMetadata("k");

            assertThat(meta.getSizeBytes()).isEqualTo(123L);
            assertThat(meta.getETag()).isEqualTo("\"abc\"");
        }

        @Test
        void deleteObject() {
            assertThat(service.deleteObject("k")).isTrue();
            verify(s3).deleteObject(any(DeleteObjectRequest.class));

            when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("x"));
            assertThat(service.deleteObject("k")).isFalse();
        }
    }
}
