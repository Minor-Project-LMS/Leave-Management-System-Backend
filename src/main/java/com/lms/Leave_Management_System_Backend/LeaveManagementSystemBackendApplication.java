package com.lms.Leave_Management_System_Backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
		UserDetailsServiceAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.lms.Leave_Management_System_Backend.repository")
public class LeaveManagementSystemBackendApplication {

	private static final Logger log = LoggerFactory.getLogger(LeaveManagementSystemBackendApplication.class);

	public static void main(String[] args) {
		log.info("Starting Leave Management System Backend application...");
		SpringApplication.run(LeaveManagementSystemBackendApplication.class, args);
		log.info("Leave Management System Backend application started successfully");
	}

}
