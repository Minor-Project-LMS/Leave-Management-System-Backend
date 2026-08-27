package com.lms.Leave_Management_System_Backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("taskExecutor")
    public void sendOtpEmail(String to, String otp) {
        String subject = "LMS Password Reset OTP";
        String body = "Your OTP for password reset is: " + otp + "\n\nThis code is valid for 5 minutes.";
        sendSimpleEmail(to, subject, body);
    }

    @Async("taskExecutor")
    public void sendSimpleEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Email recipient is blank. Skipping send.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom("lms-team@mailnator.com");
            }
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to {}. Subject: {}", to, subject, ex);
        }
    }
}
