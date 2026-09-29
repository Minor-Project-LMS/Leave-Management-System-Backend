package com.lms.Leave_Management_System_Backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_RETRY_COUNT = 3;

    private final NotificationQueueRepository notificationQueueRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationQueueRepository notificationQueueRepository,
            UserRepository userRepository,
            EmailService emailService,
            ObjectMapper objectMapper) {
        this.notificationQueueRepository = notificationQueueRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 60000) // Run every minute
    @Transactional
    public void processQueuedNotifications() {
        try {
            log.info("NotificationService: Starting to process queued notifications...");
            
            List<NotificationQueue> queuedNotifications = notificationQueueRepository.findByStatusAndScheduledAtBefore(
                    NotificationQueue.NotificationStatus.QUEUED, LocalDateTime.now());

            log.info("NotificationService: Found {} queued notifications to process", queuedNotifications.size());

            for (NotificationQueue notification : queuedNotifications) {
                try {
                    log.info("NotificationService: Processing notification ID {} for user {} with channel {}", 
                            notification.getId(), 
                            notification.getUser() != null ? notification.getUser().getEmail() : "unknown",
                            notification.getChannel());
                    processNotification(notification);
                } catch (Exception e) {
                    log.error("Error processing notification {}: {}", notification.getId(), e.getMessage(), e);
                    handleNotificationFailure(notification);
                }
            }
            
            log.info("NotificationService: Finished processing notifications");
        } catch (Exception e) {
            log.error("Error in notification processing job: {}", e.getMessage(), e);
        }
    }

    private void processNotification(NotificationQueue notification) {
        if (notification.getChannel() == NotificationQueue.Channel.EMAIL) {
            processEmailNotification(notification);
        } else {
            // IN_APP notifications are already saved in the database, no further processing needed
            notification.setStatus(NotificationQueue.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationQueueRepository.save(notification);
        }
    }

    private void processEmailNotification(NotificationQueue notification) {
        User user = notification.getUser();
        
        log.info("NotificationService: Processing email notification for user ID: {}", user != null ? user.getId() : "null");
        
        // Refresh user to get latest email
        user = userRepository.findById(user.getId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping email notification for user {} - no email found", notification.getUser().getId());
            notification.setStatus(NotificationQueue.NotificationStatus.FAILED);
            notification.setSentAt(LocalDateTime.now());
            notificationQueueRepository.save(notification);
            return;
        }

        log.info("NotificationService: User email found: {}", user.getEmail());

        // Parse payload to get title and message
        String title = "Notification";
        String message = "";
        try {
            JsonNode payload = objectMapper.readTree(notification.getPayload());
            if (payload.has("title")) {
                title = payload.get("title").asText();
            }
            if (payload.has("message")) {
                message = payload.get("message").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse notification payload: {}", e.getMessage());
        }

        log.info("NotificationService: Email title: {}, message: {}", title, message);

        // Generate email content based on template code
        String emailSubject = getEmailSubject(notification.getTemplateCode(), title);
        String emailBody = getEmailBody(notification.getTemplateCode(), title, message, user);

        log.info("NotificationService: About to send email to {} with subject: {}", user.getEmail(), emailSubject);

        // Send email
        emailService.sendHtmlEmail(user.getEmail(), emailSubject, emailBody);

        // Update notification status
        notification.setStatus(NotificationQueue.NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notificationQueueRepository.save(notification);

        log.info("Email notification sent successfully to {} for template {}", user.getEmail(), notification.getTemplateCode());
    }

    private void handleNotificationFailure(NotificationQueue notification) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        
        if (notification.getRetryCount() >= MAX_RETRY_COUNT) {
            notification.setStatus(NotificationQueue.NotificationStatus.FAILED);
            log.error("Notification {} failed after {} retries", notification.getId(), MAX_RETRY_COUNT);
        } else {
            // Keep it queued for retry
            log.warn("Notification {} failed, retry {} of {}", notification.getId(), notification.getRetryCount(), MAX_RETRY_COUNT);
        }
        
        notificationQueueRepository.save(notification);
    }

    private String getEmailSubject(String templateCode, String defaultTitle) {
        return switch (templateCode) {
            case "LEAVE_SUBMITTED" -> "Leave Request Submitted";
            case "LEAVE_APPROVED" -> "Leave Request Approved";
            case "LEAVE_REJECTED" -> "Leave Request Rejected";
            case "LEAVE_HR_APPROVAL_PENDING" -> "HR Approval Required";
            case "COMP_OFF_SUBMITTED" -> "Comp-Off Request Submitted";
            case "COMP_OFF_APPROVED" -> "Comp-Off Request Approved";
            case "COMP_OFF_REJECTED" -> "Comp-Off Request Rejected";
            case "COMP_OFF_APPROVAL_PENDING" -> "Comp-Off Approval Required";
            default -> defaultTitle;
        };
    }

    private String getEmailBody(String templateCode, String title, String message, User user) {
        String userName = user.getName() != null ? user.getName() : "User";
        
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>%s</title>
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
                                        %s
                                    </h2>
                                    <p style="color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 0 28px 0;">
                                        %s
                                    </p>

                                    <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin: 0 0 28px 0; text-align: left;">
                                        <p style="color: #64748b; font-size: 13px; line-height: 1.6; margin: 0;">
                                            <strong>Dear %s,</strong><br><br>
                                            This is an automated notification from the Leave Management System. Please log in to your dashboard for more details.
                                        </p>
                                    </div>

                                    <p style="color: #94a3b8; font-size: 12px; line-height: 1.5; margin: 0;">
                                        If you have any questions, please contact your HR department.
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
        """.formatted(title, title, message, userName);
    }
}
