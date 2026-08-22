package com.lms.Leave_Management_System_Backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
		UserDetailsServiceAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.lms.Leave_Management_System_Backend.repository")
public class LeaveManagementSystemBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaveManagementSystemBackendApplication.class, args);
	}

}
