package com.lms.Leave_Management_System_Backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.Leave_Management_System_Backend.model.OutboxEvent;
import com.lms.Leave_Management_System_Backend.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotificationEventService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventService.class);
    
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Write an outbox event in the current transaction
     * @param aggregateType The aggregate type (LEAVE_REQUEST, COMP_OFF_REQUEST, DELEGATION)
     * @param aggregateId The aggregate ID
     * @param eventType The event type (LEAVE_APPROVED, LEAVE_REJECTED, etc.)
     * @param payload The event payload as a Map
     * @param kafkaKey The Kafka partition key (typically recipient user_id)
     */
    @Transactional
    public void publishEvent(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload, String kafkaKey) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
            outboxEvent.setKafkaKey(kafkaKey);
            outboxEvent.setStatus(OutboxEvent.OutboxStatus.PENDING);
            
            outboxEventRepository.save(outboxEvent);
            
            log.debug("Published outbox event: type={}, aggregate={}, event={}", aggregateType, aggregateId, eventType);
        } catch (Exception e) {
            log.error("Failed to publish outbox event: type={}, aggregate={}, event={}", aggregateType, aggregateId, eventType, e);
            throw new RuntimeException("Failed to publish notification event", e);
        }
    }

    /**
     * Event type constants
     */
    public static class EventTypes {
        // Leave Request events
        public static final String LEAVE_SUBMITTED = "LEAVE_SUBMITTED";
        public static final String LEAVE_APPROVED = "LEAVE_APPROVED";
        public static final String LEAVE_REJECTED = "LEAVE_REJECTED";
        public static final String LEAVE_ESCALATED = "LEAVE_ESCALATED";
        public static final String LEAVE_WITHDRAWN = "LEAVE_WITHDRAWN";
        
        // Comp-Off events
        public static final String COMP_OFF_SUBMITTED = "COMP_OFF_SUBMITTED";
        public static final String COMP_OFF_APPROVED = "COMP_OFF_APPROVED";
        public static final String COMP_OFF_REJECTED = "COMP_OFF_REJECTED";
        public static final String COMP_OFF_EXPIRED = "COMP_OFF_EXPIRED";
        
        // Delegation events
        public static final String DELEGATION_CREATED = "DELEGATION_CREATED";
        public static final String DELEGATION_REVOKED = "DELEGATION_REVOKED";
    }

    /**
     * Aggregate type constants
     */
    public static class AggregateTypes {
        public static final String LEAVE_REQUEST = "LEAVE_REQUEST";
        public static final String COMP_OFF_REQUEST = "COMP_OFF_REQUEST";
        public static final String DELEGATION = "DELEGATION";
    }
}
