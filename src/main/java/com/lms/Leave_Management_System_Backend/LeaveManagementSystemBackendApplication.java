package com.lms.Leave_Management_System_Backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.lms.Leave_Management_System_Backend.repository")
@EnableRedisRepositories(basePackages = "com.lms.Leave_Management_System_Backend.repository")
public class LeaveManagementSystemBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaveManagementSystemBackendApplication.class, args);
	}

}
