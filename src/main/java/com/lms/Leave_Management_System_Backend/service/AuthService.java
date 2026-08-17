package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.UserDto;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /*
     * OTPs are temporarily stored in memory.
     * User information and passwords are stored in PostgreSQL.
     */
    private final Map<String, OtpEntry> otpStore =
            new ConcurrentHashMap<>();

    private final Random random = new Random();

    public AuthService(
            EmailService emailService,
            UserRepository userRepository) {

        this.emailService = emailService;
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Represents an OTP and its expiry time.
     */
    private static class OtpEntry {

        final String otp;
        final Instant expiresAt;

        OtpEntry(String otp, Instant expiresAt) {
            this.otp = otp;
            this.expiresAt = expiresAt;
        }
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

        // OTP valid for 5 minutes
        Instant expiry =
                Instant.now().plusSeconds(5 * 60);

        otpStore.put(
                normalizedEmail.toLowerCase(),
                new OtpEntry(otp, expiry)
        );

        try {

            emailService.sendOtpEmail(
                    normalizedEmail,
                    otp
            );

        } catch (Exception ex) {

            // Useful during local development
            System.out.println(
                    "[AuthService] OTP for "
                            + normalizedEmail
                            + " = "
                            + otp
                            + " (valid 5 minutes)"
            );
        }

        return true;
    }

    // =========================================================
    // VERIFY OTP
    // =========================================================

    /**
     * Verify OTP entered by the user.
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

        OtpEntry entry =
                otpStore.get(normalizedEmail);

        if (entry == null) {
            return false;
        }

        // Check expiry
        if (Instant.now().isAfter(entry.expiresAt)) {

            otpStore.remove(normalizedEmail);

            return false;
        }

        // Verify OTP
        boolean valid =
                entry.otp.equals(otp.trim());

        if (valid) {
            otpStore.remove(normalizedEmail);
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
            userDto.setManagerName(user.getReportsTo().getName());
        }
        userDto.setDateOfJoining(user.getDateOfJoining());
        userDto.setEmploymentStatus(user.getEmploymentStatus().name());

        return userDto;
    }
}