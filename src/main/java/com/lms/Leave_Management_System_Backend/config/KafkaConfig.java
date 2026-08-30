package com.lms.Leave_Management_System_Backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableKafka
@EnableScheduling
public class KafkaConfig {
    // Kafka configuration is primarily in application.properties
    // This class enables Kafka and scheduling for the outbox publisher
}
