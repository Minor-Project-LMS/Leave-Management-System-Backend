package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.OutboxEvent;
import com.lms.Leave_Management_System_Backend.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.template.default-topic:lms.notifications.v1}")
    private String defaultTopic;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, 
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Scheduled task to process pending outbox events
     * Runs every 10 seconds
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void processPendingEvents() {
        try {
            // Get a batch of pending events
            List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents()
                    .stream()
                    .limit(BATCH_SIZE)
                    .toList();

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.info("Processing {} pending outbox events", pendingEvents.size());

            for (OutboxEvent event : pendingEvents) {
                processEvent(event);
            }
        } catch (Exception e) {
            log.error("Error processing pending outbox events", e);
        }
    }

    /**
     * Process a single outbox event
     */
    private void processEvent(OutboxEvent event) {
        try {
            // Use the event ID as the Kafka key for proper partitioning and tracing
            String kafkaKey = event.getKafkaKey() != null ? event.getKafkaKey() : event.getId().toString();
            
            // Create a ProducerRecord with headers for tracing
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getKafkaTopic(),
                    kafkaKey,
                    event.getPayload()
            );
            
            // Add tracing headers
            record.headers().add("outbox_event_id", event.getId().toString().getBytes());
            record.headers().add("aggregate_type", event.getAggregateType().getBytes());
            record.headers().add("aggregate_id", event.getAggregateId().toString().getBytes());
            record.headers().add("event_type", event.getEventType().getBytes());
            
            // Send to Kafka
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex == null) {
                    // Success - mark as published
                    markAsPublished(event);
                } else {
                    // Failure - increment retry count and mark as failed if exceeded max retries
                    handlePublishFailure(event, ex);
                }
            });

        } catch (Exception e) {
            log.error("Failed to process outbox event: {}", event.getId(), e);
            handlePublishFailure(event, e);
        }
    }

    /**
     * Mark an event as successfully published
     */
    @Transactional
    public void markAsPublished(OutboxEvent event) {
        try {
            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            log.debug("Marked outbox event {} as published", event.getId());
        } catch (Exception e) {
            log.error("Failed to mark event {} as published", event.getId(), e);
        }
    }

    /**
     * Handle publish failure with retry logic
     */
    @Transactional
    public void handlePublishFailure(OutboxEvent event, Throwable ex) {
        try {
            int currentRetryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
            
            if (currentRetryCount >= MAX_RETRY_COUNT) {
                // Max retries exceeded - mark as failed permanently
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                event.setErrorMessage("Max retry count exceeded: " + ex.getMessage());
                log.error("Outbox event {} failed permanently after {} retries", event.getId(), currentRetryCount);
            } else {
                // Increment retry count and keep as PENDING for retry
                event.setRetryCount(currentRetryCount + 1);
                event.setErrorMessage(ex.getMessage());
                log.warn("Outbox event {} failed (attempt {}/{}), will retry", 
                         event.getId(), currentRetryCount + 1, MAX_RETRY_COUNT);
            }
            
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to handle publish failure for event {}", event.getId(), e);
        }
    }

    /**
     * Retry failed events that haven't exceeded max retries
     * Runs every 5 minutes
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryFailedEvents() {
        try {
            List<OutboxEvent> retryableEvents = outboxEventRepository.findRetryableFailedEvents()
                    .stream()
                    .limit(BATCH_SIZE)
                    .toList();

            if (retryableEvents.isEmpty()) {
                return;
            }

            log.info("Retrying {} failed outbox events", retryableEvents.size());

            for (OutboxEvent event : retryableEvents) {
                event.setStatus(OutboxEvent.OutboxStatus.PENDING);
                outboxEventRepository.save(event);
            }
        } catch (Exception e) {
            log.error("Error retrying failed outbox events", e);
        }
    }
}
