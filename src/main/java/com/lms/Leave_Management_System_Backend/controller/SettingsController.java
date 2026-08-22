package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.SystemSettings;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    @GetMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<SystemSettings> getSystemSettings(Authentication authentication) {

        // Simplified implementation - would query actual system settings
        SystemSettings settings = new SystemSettings();
        
        // General settings
        Map<String, Object> general = new HashMap<>();
        general.put("companyName", "Acme Corporation");
        general.put("companyEmail", "hr@acme.com");
        general.put("contactNumber", "+1-555-0123");
        general.put("logoUrl", "/uploads/logo.png");
        general.put("dateFormat", "DD/MM/YYYY");
        general.put("timeFormat", "24h");
        general.put("timezone", "Asia/Kolkata");
        general.put("language", "en");
        settings.setGeneral(general);

        // Leave settings
        Map<String, Object> leaveSettings = new HashMap<>();
        leaveSettings.put("maxLeaveDays", 30);
        leaveSettings.put("advanceLeaveDays", 7);
        leaveSettings.put("cancellationWindowDays", 2);
        leaveSettings.put("minNoticePeriodDays", 3);
        leaveSettings.put("probationAccessEnabled", true);
        leaveSettings.put("carryForwardEnabled", true);
        settings.setLeaveSettings(leaveSettings);

        // Approval workflow
        Map<String, Object> approvalWorkflow = new HashMap<>();
        approvalWorkflow.put("twoStepApprovalEnabled", true);
        approvalWorkflow.put("autoApproveSmallLeaves", false);
        approvalWorkflow.put("backdatedLeaveRestricted", true);
        settings.setApprovalWorkflow(approvalWorkflow);

        // System preferences
        Map<String, Object> systemPreferences = new HashMap<>();
        systemPreferences.put("employeeSelfRegistration", false);
        systemPreferences.put("leaveBalanceDisplayEnabled", true);
        systemPreferences.put("weekendSelectionEnabled", true);
        systemPreferences.put("holidayDisplayEnabled", true);
        settings.setSystemPreferences(systemPreferences);

        // Notifications
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("emailProvider", "SendGrid");
        notifications.put("smsProvider", "Twilio");
        settings.setNotifications(notifications);

        // Security
        Map<String, Object> security = new HashMap<>();
        security.put("passwordMinLength", 8);
        security.put("ssoGoogleEnabled", false);
        security.put("ssoMicrosoftEnabled", false);
        settings.setSecurity(security);

        // Integrations
        Map<String, Object> integrations = new HashMap<>();
        integrations.put("calendarSyncProvider", "Google");
        integrations.put("ssoProvider", null);
        settings.setIntegrations(integrations);

        // System info (read-only)
        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("version", "1.0.0");
        systemInfo.put("environment", "production");
        systemInfo.put("databaseVersion", "PostgreSQL 14.2");
        systemInfo.put("lastUpdatedBy", "admin@acme.com");
        systemInfo.put("lastUpdatedAt", "2024-01-15T10:30:00Z");
        settings.setSystemInfo(systemInfo);

        return ResponseEntity.ok(settings);
    }

    @PatchMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<SystemSettings> updateSystemSettings(
            @RequestBody SystemSettings settings,
            Authentication authentication) {

        // Simplified implementation - would update actual system settings
        // In real implementation, validate and save only the provided sections
        
        return ResponseEntity.ok(settings);
    }
}