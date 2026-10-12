package com.lms.Leave_Management_System_Backend.service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@lms.com");
    }

    @Test
    void sendsHtmlEmail() throws Exception {
        emailService.sendHtmlEmail("asha@lms.com", "Hello", "<b>Hi</b>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Hello");
        assertThat(((InternetAddress) sent.getAllRecipients()[0]).getAddress()).isEqualTo("asha@lms.com");
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("noreply@lms.com");
    }

    @Test
    void otpEmailUsesResetSubject() throws Exception {
        emailService.sendOtpEmail("asha@lms.com", "482913");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("LMS — Password Reset Verification");
    }

    @Test
    void blankRecipientIsSkipped() {
        emailService.sendHtmlEmail(" ", "s", "b");
        emailService.sendHtmlEmail(null, "s", "b");
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("BUG GUARD: with spring.mail.username empty, the From address is invalid and nothing is sent")
    void emptyFromAddressSilentlyDropsEmail() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "");

        emailService.sendHtmlEmail("asha@lms.com", "Hello", "<b>Hi</b>");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
