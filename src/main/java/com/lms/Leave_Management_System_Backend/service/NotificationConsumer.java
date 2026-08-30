package com.lms.Leave_Management_System_Backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.Leave_Management_System_Backend.model.NotificationQueue;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.NotificationQueueRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    
    private final NotificationQueueRepository notificationQueueRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationQueueRepository notificationQueueRepository,
                               UserRepository userRepository,
                               EmailService emailService,
                               ObjectMapper objectMapper) {
        this.notificationQueueRepository = notificationQueueRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${spring.kafka.template.default-topic:lms.notifications.v1}",
            groupId = "${spring.kafka.consumer.group-id:lms-notification-worker}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeNotification(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            
            String eventType = event.get("event_type").asText();
            String aggregateType = event.get("aggregate_type").asText();
            Long aggregateId = event.get("aggregate_id").asLong();
            JsonNode payload = event.get("payload");
            
            // Extract source event ID from headers or use a correlation ID
            // For now, we'll use the offset as a temporary unique identifier
            Long sourceEventId = record.offset();
            
            log.info("Consumed notification event: type={}, aggregate={}, partition={}, offset={}", 
                     eventType, aggregateType, record.partition(), record.offset());
            
            // Resolve recipients and channels for this event type
            List<NotificationTarget> targets = resolveNotificationTargets(eventType, aggregateType, aggregateId, payload);
            
            // Create notification queue entries for each target
            for (NotificationTarget target : targets) {
                upsertNotificationQueue(target, record, sourceEventId);
            }
            
            // Acknowledge the message
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error consuming notification event", e);
            // Don't acknowledge - let Kafka retry
        }
    }

    /**
     * Resolve notification targets (recipients and channels) for an event
     */
    private List<NotificationTarget> resolveNotificationTargets(String eventType, String aggregateType, 
                                                                 Long aggregateId, JsonNode payload) {
        List<NotificationTarget> targets = new ArrayList<>();
        
        try {
            switch (eventType) {
                case NotificationEventService.EventTypes.LEAVE_APPROVED:
                case NotificationEventService.EventTypes.LEAVE_REJECTED:
                case NotificationEventService.EventTypes.LEAVE_ESCALATED:
                    // Notify the leave request owner
                    Long userId = payload.has("user_id") ? payload.get("user_id").asLong() : null;
                    if (userId != null) {
                        targets.add(new NotificationTarget(userId, NotificationQueue.Channel.IN_APP, 
                                getTemplateCode(eventType), payload.toString()));
                        targets.add(new NotificationTarget(userId, NotificationQueue.Channel.EMAIL, 
                                getTemplateCode(eventType), payload.toString()));
                    }
                    break;
                    
                case NotificationEventService.EventTypes.LEAVE_SUBMITTED:
                    // Notify the approver
                    Long approverId = payload.has("approver_id") ? payload.get("approver_id").asLong() : null;
                    if (approverId != null) {
                        targets.add(new NotificationTarget(approverId, NotificationQueue.Channel.IN_APP, 
                                "LEAVE_APPROVAL_REQUIRED", payload.toString()));
                        targets.add(new NotificationTarget(approverId, NotificationQueue.Channel.EMAIL, 
                                "LEAVE_APPROVAL_REQUIRED", payload.toString()));
                    }
                    break;
                    
                case NotificationEventService.EventTypes.COMP_OFF_APPROVED:
                case NotificationEventService.EventTypes.COMP_OFF_REJECTED:
                    // Notify the comp-off request owner
                    Long compOffUserId = payload.has("user_id") ? payload.get("user_id").asLong() : null;
                    if (compOffUserId != null) {
                        targets.add(new NotificationTarget(compOffUserId, NotificationQueue.Channel.IN_APP, 
                                getTemplateCode(eventType), payload.toString()));
                        targets.add(new NotificationTarget(compOffUserId, NotificationQueue.Channel.EMAIL, 
                                getTemplateCode(eventType), payload.toString()));
                    }
                    break;
                    
                case NotificationEventService.EventTypes.DELEGATION_CREATED:
                case NotificationEventService.EventTypes.DELEGATION_REVOKED:
                    // Notify the delegate
                    Long delegateId = payload.has("delegate_id") ? payload.get("delegate_id").asLong() : null;
                    if (delegateId != null) {
                        targets.add(new NotificationTarget(delegateId, NotificationQueue.Channel.IN_APP, 
                                getTemplateCode(eventType), payload.toString()));
                        targets.add(new NotificationTarget(delegateId, NotificationQueue.Channel.EMAIL, 
                                getTemplateCode(eventType), payload.toString()));
                    }
                    break;
                    
                default:
                    log.warn("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error resolving notification targets for event type: {}", eventType, e);
        }
        
        return targets;
    }

    /**
     * Get template code for event type
     */
    private String getTemplateCode(String eventType) {
        return eventType; // For now, use event type as template code
    }

    /**
     * Upsert notification queue entry with idempotency
     */
    private void upsertNotificationQueue(NotificationTarget target, ConsumerRecord<String, String> record, Long sourceEventId) {
        try {
            // Check if notification already exists (idempotency check)
            Optional<NotificationQueue> existing = notificationQueueRepository
                    .findBySourceEventIdAndChannelAndUserId(
                            sourceEventId,
                            target.channel, 
                            target.userId);
            
            if (existing.isPresent()) {
                log.debug("Notification already exists for event {} channel {} user {}, skipping", 
                         sourceEventId, target.channel, target.userId);
                return;
            }
            
            // Create new notification
            NotificationQueue notification = new NotificationQueue();
            User user = userRepository.findById(target.userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + target.userId));
            
            notification.setUser(user);
            notification.setChannel(target.channel);
            notification.setTemplateCode(target.templateCode);
            notification.setPayload(target.payload);
            notification.setRelatedEntityType(getRelatedEntityType(record.value()));
            notification.setRelatedEntityId(sourceEventId);
            notification.setStatus(NotificationQueue.NotificationStatus.QUEUED);
            notification.setSourceEventId(sourceEventId);
            notification.setKafkaTopic(record.topic());
            notification.setKafkaPartition(record.partition());
            notification.setKafkaOffset(record.offset());
            notification.setConsumerGroup("lms-notification-worker");
            
            notificationQueueRepository.save(notification);
            
            // If EMAIL, send immediately
            if (target.channel == NotificationQueue.Channel.EMAIL) {
                sendEmailNotification(notification, user);
            }
            
            log.info("Created notification queue entry: user={}, channel={}, template={}", 
                     user.getEmail(), target.channel, target.templateCode);
            
        } catch (Exception e) {
            log.error("Error upserting notification queue entry", e);
        }
    }

    /**
     * Send email notification
     */
    private void sendEmailNotification(NotificationQueue notification, User user) {
        try {
            notification.setStatus(NotificationQueue.NotificationStatus.IN_PROGRESS);
            notificationQueueRepository.save(notification);
            
            // Parse payload to get email content
            JsonNode payload = objectMapper.readTree(notification.getPayload());
            String subject = notification.getTemplateCode(); // Simple subject for now
            String body = payload.toString(); // Simple body for now
            
            emailService.sendSimpleEmail(user.getEmail(), subject, body);
            
            notification.setStatus(NotificationQueue.NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationQueueRepository.save(notification);
            
            log.info("Email sent successfully to {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("Failed to send email to {}", user.getEmail(), e);
            notification.setStatus(NotificationQueue.NotificationStatus.FAILED);
            notification.setRetryCount((notification.getRetryCount() != null ? notification.getRetryCount() : 0) + 1);
            notificationQueueRepository.save(notification);
        }
    }

    /**
     * Get related entity type from event
     */
    private String getRelatedEntityType(String eventJson) {
        try {
            JsonNode event = objectMapper.readTree(eventJson);
            return event.get("aggregate_type").asText();
        } catch (Exception e) {
            return "EVENT";
        }
    }

    /**
     * Inner class for notification target
     */
    private static class NotificationTarget {
        Long userId;
        NotificationQueue.Channel channel;
        String templateCode;
        String payload;
        
        NotificationTarget(Long userId, NotificationQueue.Channel channel, String templateCode, String payload) {
            this.userId = userId;
            this.channel = channel;
            this.templateCode = templateCode;
            this.payload = payload;
        }
    }
}
