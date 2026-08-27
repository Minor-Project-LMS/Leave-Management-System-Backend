package com.lms.Leave_Management_System_Backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class ActuatorConfig {

    @Bean
    public HealthIndicator redisHealthIndicator(@Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        return () -> {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                return Health.up()
                        .withDetail("redis", "Redis connection is healthy")
                        .build();
            } catch (Exception ex) {
                return Health.down()
                        .withDetail("redis", "Redis connection failed")
                        .withException(ex)
                        .build();
            }
        };
    }

    @Bean
    public HealthIndicator customHealthIndicator() {
        return () -> {
            // Custom health checks can be added here
            // For example: checking external services, disk space, etc.
            return Health.up()
                    .withDetail("application", "Leave Management System is running")
                    .withDetail("status", "All systems operational")
                    .build();
        };
    }
}