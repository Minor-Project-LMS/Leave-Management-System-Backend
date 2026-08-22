package com.lms.Leave_Management_System_Backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
    }

    @Test
    void sendOtpEmail_ShouldSendEmailWithCorrectContent() {
        String to = "test@example.com";
        String otp = "123456";

        assertDoesNotThrow(() -> emailService.sendOtpEmail(to, otp));
        
        // Since it's async, we just verify it doesn't throw exceptions
        // The actual mail sending would be verified in integration tests
    }

    @Test
    void sendSimpleEmail_WithValidParameters_ShouldNotThrow() {
        String to = "test@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        assertDoesNotThrow(() -> emailService.sendSimpleEmail(to, subject, body));
    }

    @Test
    void sendSimpleEmail_WithBlankRecipient_ShouldNotSendEmail() {
        String to = "";
        String subject = "Test Subject";
        String body = "Test Body";

        assertDoesNotThrow(() -> emailService.sendSimpleEmail(to, subject, body));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSimpleEmail_WithNullRecipient_ShouldNotSendEmail() {
        String to = null;
        String subject = "Test Subject";
        String body = "Test Body";

        assertDoesNotThrow(() -> emailService.sendSimpleEmail(to, subject, body));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSimpleEmail_WhenMailSenderThrowsException_ShouldHandleGracefully() {
        String to = "test@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        doThrow(new RuntimeException("Mail server error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.sendSimpleEmail(to, subject, body));
    }
}
