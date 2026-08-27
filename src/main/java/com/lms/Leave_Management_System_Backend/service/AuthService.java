package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.RegisterRequest;
import com.lms.Leave_Management_System_Backend.dto.UserDto;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.Role;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.RoleRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String OTP_PREFIX = "otp:";
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(5);

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> otpRedisTemplate;

    private final Random random = new Random();

    public AuthService(
            EmailService emailService,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            RoleRepository roleRepository,
            @Qualifier("otpRedisTemplate") RedisTemplate<String, String> otpRedisTemplate) {

        this.emailService = emailService;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.otpRedisTemplate = otpRedisTemplate;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // =========================================================
    // LOGIN
    // =========================================================

    /**
     * Authenticate user from PostgreSQL.
     *
     * Flow:
     *
     * email/password
     *       ↓
     * UserRepository
     *       ↓
     * app_users
     *       ↓
     * BCrypt password verification
     *       ↓
     * UserDto
     */
    public UserDto authenticate(String email, String password) {

        if (email == null || email.isBlank()) {
            return null;
        }

        if (password == null || password.isBlank()) {
            return null;
        }

        String normalizedEmail = email.trim();

        User user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElse(null);

        if (user == null) {
            return null;
        }

        if (!verifyPassword(
                password,
                user.getPasswordHash())) {

            return null;
        }

        return toUserDto(user);
    }

    // =========================================================
    // GET USER
    // =========================================================

    /**
     * Get user from PostgreSQL by email.
     *
     * Used by the refresh-token endpoint.
     */
    public UserDto getUserByEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        return userRepository
                .findByEmailIgnoreCase(email.trim())
                .map(this::toUserDto)
                .orElse(null);
    }

    // =========================================================
    // FORGOT PASSWORD
    // =========================================================

    /**
     * Generate a 6-digit OTP for password reset.
     *
     * User is checked against PostgreSQL.
     * OTP is stored in Redis with 5-minute expiration.
     */
    public boolean generateOtpForEmail(String email) {

        if (email == null || email.isBlank()) {
            return false;
        }

        String normalizedEmail = email.trim();

        // Check user exists in PostgreSQL
        User user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElse(null);

        if (user == null) {
            return false;
        }

        // Generate 6-digit OTP
        String otp = String.format(
                "%06d",
                random.nextInt(1_000_000)
        );

        // Store OTP in Redis with 5-minute expiration
        String redisKey = OTP_PREFIX + normalizedEmail.toLowerCase();
        otpRedisTemplate.opsForValue().set(redisKey, otp, OTP_EXPIRY);

        try {

            emailService.sendOtpEmail(
                    normalizedEmail,
                    otp
            );

        } catch (Exception ex) {

            // Log email failure without exposing sensitive OTP data
            log.warn("Failed to send OTP email to {}", normalizedEmail, ex);
        }

        return true;
    }

    // =========================================================
    // VERIFY OTP
    // =========================================================

    /**
     * Verify OTP entered by the user.
     * OTP is retrieved from Redis.
     */
    public boolean verifyOtp(
            String email,
            String otp) {

        if (email == null ||
                email.isBlank() ||
                otp == null ||
                otp.isBlank()) {

            return false;
        }

        String normalizedEmail =
                email.trim().toLowerCase();

        String redisKey = OTP_PREFIX + normalizedEmail;
        String storedOtp = otpRedisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            return false;
        }

        // Verify OTP
        boolean valid = storedOtp.equals(otp.trim());

        if (valid) {
            // Remove OTP from Redis after successful verification
            otpRedisTemplate.delete(redisKey);
        }

        return valid;
    }

    // =========================================================
    // RESET PASSWORD
    // =========================================================

    /**
     * Reset password in PostgreSQL.
     *
     * IMPORTANT:
     * The new password is BCrypt hashed before
     * being stored in app_users.password_hash.
     */
    public boolean resetPassword(
            String email,
            String newPassword) {

        if (email == null ||
                email.isBlank() ||
                newPassword == null ||
                newPassword.isBlank()) {

            return false;
        }

        String normalizedEmail =
                email.trim();

        User user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElse(null);

        if (user == null) {
            return false;
        }

        // Hash password before storing
        String hashedPassword =
                passwordEncoder.encode(newPassword);

        user.setPasswordHash(hashedPassword);

        // Save updated password to PostgreSQL
        userRepository.save(user);

        return true;
    }

    // =========================================================
    // PASSWORD VERIFICATION
    // =========================================================

    private boolean verifyPassword(
            String plainPassword,
            String passwordHash) {

        if (passwordHash == null ||
                passwordHash.isBlank()) {

            return false;
        }

        return passwordEncoder.matches(
                plainPassword,
                passwordHash
        );
    }

    // =========================================================
    // USER → DTO
    // =========================================================

    private UserDto toUserDto(User user) {

        String roleCode = null;

        if (user.getRole() != null) {
            roleCode =
                    user.getRole().getRoleCode();
        }

        UserDto userDto = new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                roleCode
        );

        // Add additional fields if needed for profile
        userDto.setEmployeeCode(user.getEmployeeCode());
        if (user.getDepartment() != null) {
            userDto.setDepartmentId(user.getDepartment().getId());
            userDto.setDepartmentName(user.getDepartment().getName());
        }
        if (user.getReportsTo() != null) {
            userDto.setManagerId(user.getReportsTo().getId());
            // Safe getName() call with null check
            String managerName = user.getReportsTo().getName();
            userDto.setManagerName(managerName != null ? managerName : "Unknown");
        }
        userDto.setDateOfJoining(user.getDateOfJoining());
        userDto.setEmploymentStatus(user.getEmploymentStatus().name());

        return userDto;
    }

    // =========================================================
    // REGISTER USER
    // =========================================================

    /**
     * Register a new user (self-registration).
     */
    public UserDto registerUser(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already in use");
        }

        // Check if employee code already exists (if provided)
        if (request.getEmployeeCode() != null && 
            userRepository.findByEmployeeCode(request.getEmployeeCode()).isPresent()) {
            throw new ConflictException("Employee code already in use");
        }

        // Get department
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ConflictException("Invalid department ID"));

        // Get default role (EMPLOYEE)
        Role employeeRole = roleRepository.findByRoleCode("EMPLOYEE")
                .orElseThrow(() -> new ConflictException("Default EMPLOYEE role not found"));

        // Create new user
        User user = new User();
        user.setName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmployeeCode(request.getEmployeeCode());
        user.setDepartment(department);
        user.setRole(employeeRole);
        user.setEmploymentStatus(User.EmploymentStatus.ACTIVE);
        user.setDateOfJoining(java.time.LocalDate.now());

        User savedUser = userRepository.save(user);
        return toUserDto(savedUser);
    }
}