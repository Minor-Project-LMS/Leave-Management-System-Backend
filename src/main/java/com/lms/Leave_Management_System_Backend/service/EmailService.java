package com.lms.Leave_Management_System_Backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        String subject = "LMS — Password Reset Verification";
        String htmlBody = getOtpEmailTemplate(otp);

        sendHtmlEmail(to, subject, htmlBody);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (to == null || to.isBlank()) {
            log.warn("Email send skipped: recipient address is null or blank.");
            return;
        }

        log.info("Initiating HTML email send | From: {} | To: {} | Subject: {}", fromAddress, to, subject);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("Email sent successfully | To: {} | Subject: {}", to, subject);

        } catch (MessagingException ex) {
            log.error("Email send failed | To: {} | Subject: {} | Exception: {} | Message: {}",
                    to, subject, ex.getClass().getName(), ex.getMessage(), ex);
        }
    }

    private String getOtpEmailTemplate(String otp) {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Password Reset OTP</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #f4f6f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
            <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="background-color: #f4f6f9; padding: 40px 0;">
                <tr>
                    <td align="center">
                        <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 480px; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); overflow: hidden;">
                            <!-- Header -->
                            <tr>
                                <td style="background-color: #0f172a; padding: 28px 32px; text-align: center;">
                                    <h1 style="color: #ffffff; font-size: 20px; font-weight: 600; margin: 0; letter-spacing: 0.5px;">
                                        Leave Management System
                                    </h1>
                                </td>
                            </tr>

                            <!-- Body -->
                            <tr>
                                <td style="padding: 36px 32px; text-align: center;">
                                    <h2 style="color: #1e293b; font-size: 18px; font-weight: 600; margin: 0 0 12px 0;">
                                        Password Reset Verification
                                    </h2>
                                    <p style="color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 0 28px 0;">
                                        Use the verification code below to reset your password. This code will expire in <strong style="color: #0f172a;">5 minutes</strong>.
                                    </p>

                                    <!-- OTP Display Box -->
                                    <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin: 0 0 28px 0;">
                                        <span style="font-family: 'Courier New', Courier, monospace; font-size: 32px; font-weight: 700; color: #2563eb; letter-spacing: 8px; display: inline-block;">
                                            %s
                                        </span>
                                    </div>

                                    <p style="color: #94a3b8; font-size: 12px; line-height: 1.5; margin: 0;">
                                        If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged.
                                    </p>
                                </td>
                            </tr>

                            <!-- Footer -->
                            <tr>
                                <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 32px; text-align: center;">
                                    <p style="color: #94a3b8; font-size: 11px; margin: 0;">
                                        &copy; LMS. All rights reserved.
                                    </p>
                                </td>
                            </tr>

                        </table>
                    </td>
                </tr>
            </table>
        </body>
        </html>
        """.formatted(otp);
    }
}