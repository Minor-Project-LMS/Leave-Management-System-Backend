package com.lms.Leave_Management_System_Backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    public void sendOtpEmail(String to, String otp) {
        String subject = "LMS Password Reset OTP";
        String body = "Your OTP for password reset is: " + otp + "\n\nThis code is valid for 5 minutes.";
        sendSimpleEmail(to, subject, body);
    }

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
                message.setFrom(fromAddress);
            }
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to {}. Falling back to console output.", to, ex);
            System.out.println("[EmailService] To: " + to);
            System.out.println("[EmailService] Subject: " + subject);
            System.out.println("[EmailService] Body: " + body);
        }
    }
}
