package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.dto.UserDto;
import com.lms.Leave_Management_System_Backend.model.User;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class AuthService {

    private final EmailService emailService;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public AuthService(EmailService emailService) {
        this.emailService = emailService;
    }

    // store OTPs with expiry
    private static class OtpEntry {
        final String otp;
        final Instant expiresAt;

        OtpEntry(String otp, Instant expiresAt) {
            this.otp = otp;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    private void initDemoUser() {
        String email = "john.doe@company.com";
        String hashed = hashPassword("Password@123");
        users.put(email.toLowerCase(), new User(1L, "John Doe", email, hashed, "EMPLOYEE"));

        String adminEmail = "admin@company.com";
        String adminHash = hashPassword("Admin@123");
        users.put(adminEmail.toLowerCase(), new User(2L, "Admin User", adminEmail, adminHash, "HR_ADMIN"));
    }

    public UserDto authenticate(String email, String password) {
        if (email == null || password == null) return null;
        User u = users.get(email.toLowerCase());
        if (u == null) return null;
        if (verifyPassword(password, u.getPasswordHash())) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole());
        }
        return null;
    }

    public UserDto getUserByEmail(String email) {
        if (email == null) return null;
        User u = users.get(email.toLowerCase());
        if (u == null) return null;
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }

    public boolean generateOtpForEmail(String email) {
        if (email == null) return false;
        User u = users.get(email.toLowerCase());
        if (u == null) return false;
        String otp = String.format("%06d", random.nextInt(1_000_000));
        Instant expiry = Instant.now().plusSeconds(5 * 60); // 5 minutes
        otpStore.put(email.toLowerCase(), new OtpEntry(otp, expiry));

        try {
            emailService.sendOtpEmail(email, otp);
        } catch (Exception ex) {
            System.out.println("[AuthService] OTP for " + email + " = " + otp + " (valid 5 minutes)");
        }
        return true;
    }

    public boolean verifyOtp(String email, String otp) {
        if (email == null || otp == null) return false;
        OtpEntry e = otpStore.get(email.toLowerCase());
        if (e == null) return false;
        if (Instant.now().isAfter(e.expiresAt)) {
            otpStore.remove(email.toLowerCase());
            return false;
        }
        boolean ok = e.otp.equals(otp);
        if (ok) otpStore.remove(email.toLowerCase());
        return ok;
    }

    public boolean resetPassword(String email, String newPassword) {
        if (email == null || newPassword == null) return false;
        User u = users.get(email.toLowerCase());
        if (u == null) return false;
        u.setPasswordHash(hashPassword(newPassword));
        return true;
    }

    private String hashPassword(String plain) {
        return passwordEncoder.encode(plain);
    }

    private boolean verifyPassword(String plain, String hash) {
        if (hash == null) return false;
        return passwordEncoder.matches(plain, hash);
    }
}
