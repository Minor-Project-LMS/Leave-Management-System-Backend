package com.lms.Leave_Management_System_Backend.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class ApplicationInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> details = new HashMap<>();
        details.put("application", "Leave Management System Backend");
        details.put("version", "1.0.0");
        details.put("description", "Enterprise Leave Management System API");
        details.put("startupTime", LocalDateTime.now().toString());
        details.put("features", new String[]{
            "User Authentication",
            "Leave Request Management",
            "Approval Workflows",
            "Department Management",
            "Holiday Management",
            "Audit Trail",
            "Dashboard Analytics"
        });
        
        builder.withDetail("application", details);
    }
}