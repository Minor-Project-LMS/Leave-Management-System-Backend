package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.RegisterRequest;
import com.lms.Leave_Management_System_Backend.dto.UserDto;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.RoleRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private EmailService emailService;
    private UserRepository userRepository;
    private DepartmentRepository departmentRepository;
    private RoleRepository roleRepository;
    private RedisTemplate<String, String> redis;
    private ValueOperations<String, String> valueOps;
    private AttachmentService attachmentService;
    private AuthService authService;

    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emailService = mock(EmailService.class);
        userRepository = mock(UserRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        roleRepository = mock(RoleRepository.class);
        redis = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        attachmentService = mock(AttachmentService.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        authService = new AuthService(emailService, userRepository, departmentRepository,
                roleRepository, redis, attachmentService);

        user = employee(1L, "asha@lms.com");
        user.setName("Asha");
        user.setPasswordHash(ENCODER.encode("Secret@123"));
        user.setDepartment(department(10, "Engineering"));
        user.setReportsTo(manager(2L, "boss@lms.com"));
        when(attachmentService.resolveAvatarUrl(1L)).thenReturn("https://cdn/avatar.png");
    }

    @Nested
    class Authenticate {

        @Test
        @DisplayName("returns a fully populated UserDto for correct credentials")
        void success() {
            when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));

            UserDto dto = authService.authenticate("  asha@lms.com ", "Secret@123");

            assertThat(dto).isNotNull();
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getRole()).isEqualTo("EMPLOYEE");
            assertThat(dto.getDepartmentId()).isEqualTo(10);
            assertThat(dto.getDepartmentName()).isEqualTo("Engineering");
            assertThat(dto.getManagerId()).isEqualTo(2L);
            assertThat(dto.getManagerName()).isEqualTo("Manager 2");
            assertThat(dto.getEmploymentStatus()).isEqualTo("ACTIVE");
            assertThat(dto.getAvatarUrl()).isEqualTo("https://cdn/avatar.png");
            assertThat(dto.getAvatarAttachmentId()).isNull();
        }

        @Test
        void wrongPassword() {
            when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));
            assertThat(authService.authenticate("asha@lms.com", "wrong")).isNull();
        }

        @Test
        void unknownUser() {
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            assertThat(authService.authenticate("nobody@lms.com", "Secret@123")).isNull();
        }

        @Test
        void blankInputsShortCircuit() {
            assertThat(authService.authenticate(null, "x")).isNull();
            assertThat(authService.authenticate(" ", "x")).isNull();
            assertThat(authService.authenticate("a@b.com", null)).isNull();
            assertThat(authService.authenticate("a@b.com", " ")).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        void userWithoutPasswordHashCannotLogIn() {
            user.setPasswordHash(null);
            when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));
            assertThat(authService.authenticate("asha@lms.com", "Secret@123")).isNull();
        }

        @Test
        void managerWithoutNameShowsUnknown() {
            user.getReportsTo().setName(null);
            when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));
            assertThat(authService.authenticate("asha@lms.com", "Secret@123").getManagerName()).isEqualTo("Unknown");
        }
    }

    @Test
    void getUserByEmail() {
        when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));

        assertThat(authService.getUserByEmail(" asha@lms.com ").getEmail()).isEqualTo("asha@lms.com");
        assertThat(authService.getUserByEmail(null)).isNull();
        assertThat(authService.getUserByEmail("missing@lms.com")).isNull();
    }

    @Nested
    class ForgotPasswordOtp {

        @Test
        @DisplayName("stores a 6-digit OTP in Redis for 5 minutes and emails it")
        void generatesOtp() {
            when(userRepository.findByEmailIgnoreCase("Asha@LMS.com")).thenReturn(Optional.of(user));

            assertThat(authService.generateOtpForEmail("Asha@LMS.com")).isTrue();

            ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
            verify(valueOps).set(eq("otp:asha@lms.com"), otp.capture(), eq(Duration.ofMinutes(5)));
            assertThat(otp.getValue()).matches("\\d{6}");
            verify(emailService).sendOtpEmail("Asha@LMS.com", otp.getValue());
        }

        @Test
        void unknownEmailReturnsFalse() {
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            assertThat(authService.generateOtpForEmail("x@lms.com")).isFalse();
            verifyNoInteractions(valueOps, emailService);
        }

        @Test
        void blankEmailReturnsFalse() {
            assertThat(authService.generateOtpForEmail(" ")).isFalse();
            assertThat(authService.generateOtpForEmail(null)).isFalse();
        }

        @Test
        @DisplayName("an email failure does not fail the request (OTP is still stored)")
        void emailFailureIsSwallowed() {
            when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("smtp down")).when(emailService).sendOtpEmail(anyString(), anyString());

            assertThat(authService.generateOtpForEmail("asha@lms.com")).isTrue();
            verify(valueOps).set(eq("otp:asha@lms.com"), anyString(), any(Duration.class));
        }
    }

    @Nested
    class VerifyOtp {

        @Test
        void correctOtpIsConsumed() {
            when(valueOps.get("otp:asha@lms.com")).thenReturn("123456");

            assertThat(authService.verifyOtp("ASHA@lms.com", " 123456 ")).isTrue();
            verify(redis).delete("otp:asha@lms.com");
        }

        @Test
        void wrongOtpIsKept() {
            when(valueOps.get("otp:asha@lms.com")).thenReturn("123456");

            assertThat(authService.verifyOtp("asha@lms.com", "000000")).isFalse();
            verify(redis, never()).delete(anyString());
        }

        @Test
        void expiredOtp() {
            when(valueOps.get(anyString())).thenReturn(null);
            assertThat(authService.verifyOtp("asha@lms.com", "123456")).isFalse();
        }

        @Test
        void blankInputs() {
            assertThat(authService.verifyOtp(null, "1")).isFalse();
            assertThat(authService.verifyOtp("a@b.com", " ")).isFalse();
        }
    }

    @Nested
    class ResetPassword {

        @Test
        @DisplayName("stores a BCrypt hash, never the plain password")
        void hashesNewPassword() {
            when(userRepository.findByEmailIgnoreCase("asha@lms.com")).thenReturn(Optional.of(user));

            assertThat(authService.resetPassword("asha@lms.com", "NewPass@1")).isTrue();

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("NewPass@1");
            assertThat(ENCODER.matches("NewPass@1", saved.getValue().getPasswordHash())).isTrue();
        }

        @Test
        void unknownUser() {
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            assertThat(authService.resetPassword("x@lms.com", "NewPass@1")).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        void blankInputs() {
            assertThat(authService.resetPassword("", "x")).isFalse();
            assertThat(authService.resetPassword("a@b.com", null)).isFalse();
        }
    }

    @Nested
    class Register {

        private RegisterRequest request() {
            RegisterRequest r = new RegisterRequest();
            r.setFullName("New Joiner");
            r.setEmail("new@lms.com");
            r.setPassword("Password@1");
            r.setDepartmentId(10);
            r.setEmployeeCode("EMP900");
            return r;
        }

        @Test
        void createsActiveEmployee() {
            when(userRepository.findByEmailIgnoreCase("new@lms.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmployeeCode("EMP900")).thenReturn(Optional.empty());
            when(departmentRepository.findById(10)).thenReturn(Optional.of(department(10, "Engineering")));
            when(roleRepository.findByRoleCode("EMPLOYEE")).thenReturn(Optional.of(role("EMPLOYEE")));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(99L);
                return u;
            });

            UserDto dto = authService.registerUser(request());

            assertThat(dto.getId()).isEqualTo(99L);
            assertThat(dto.getRole()).isEqualTo("EMPLOYEE");
            assertThat(dto.getEmploymentStatus()).isEqualTo("ACTIVE");
            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(ENCODER.matches("Password@1", saved.getValue().getPasswordHash())).isTrue();
        }

        @Test
        void duplicateEmail() {
            when(userRepository.findByEmailIgnoreCase("new@lms.com")).thenReturn(Optional.of(user));
            assertThatThrownBy(() -> authService.registerUser(request()))
                    .isInstanceOf(ConflictException.class).hasMessage("Email already in use");
        }

        @Test
        void duplicateEmployeeCode() {
            when(userRepository.findByEmailIgnoreCase("new@lms.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmployeeCode("EMP900")).thenReturn(Optional.of(user));
            assertThatThrownBy(() -> authService.registerUser(request()))
                    .isInstanceOf(ConflictException.class).hasMessage("Employee code already in use");
        }

        @Test
        void invalidDepartment() {
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            when(userRepository.findByEmployeeCode(anyString())).thenReturn(Optional.empty());
            when(departmentRepository.findById(10)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> authService.registerUser(request()))
                    .isInstanceOf(ConflictException.class).hasMessage("Invalid department ID");
        }

        @Test
        void missingEmployeeRole() {
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            when(userRepository.findByEmployeeCode(anyString())).thenReturn(Optional.empty());
            when(departmentRepository.findById(10)).thenReturn(Optional.of(department(10, "Engineering")));
            when(roleRepository.findByRoleCode("EMPLOYEE")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> authService.registerUser(request()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("EMPLOYEE role not found");
        }
    }
}
