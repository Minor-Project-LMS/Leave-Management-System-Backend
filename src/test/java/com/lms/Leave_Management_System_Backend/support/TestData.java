package com.lms.Leave_Management_System_Backend.support;

import com.lms.Leave_Management_System_Backend.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Factory helpers so every test builds entities the same way.
 * Entities are plain POJOs here; no database is involved.
 */
public final class TestData {

    private TestData() {
    }

    public static Role role(String code) {
        Role role = new Role();
        role.setId(switch (code) {
            case "HR_ADMIN" -> 3;
            case "MANAGER" -> 2;
            default -> 1;
        });
        role.setRoleCode(code);
        role.setRoleDescription(code + " role");
        return role;
    }

    public static Department department(Integer id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setDepartmentName(name);
        return department;
    }

    public static User user(Long id, String name, String email, String roleCode) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setRole(role(roleCode));
        user.setEmploymentStatus(User.EmploymentStatus.ACTIVE);
        user.setEmployeeCode("EMP" + String.format("%03d", id));
        user.setDateOfJoining(LocalDate.of(2024, 1, 15));
        return user;
    }

    public static User employee(Long id, String email) {
        return user(id, "Employee " + id, email, "EMPLOYEE");
    }

    public static User manager(Long id, String email) {
        return user(id, "Manager " + id, email, "MANAGER");
    }

    public static User hr(Long id, String email) {
        return user(id, "HR " + id, email, "HR_ADMIN");
    }

    public static LeaveCategory category(Integer id, String name) {
        LeaveCategory category = new LeaveCategory();
        category.setId(id);
        category.setCategoryName(name);
        category.setPaid(true);
        category.setDefaultAnnualQuota(12.0);
        category.setStatus("ACTIVE");
        category.setCategoryType("GENERAL");
        return category;
    }

    public static LeaveLedger ledger(User user, LeaveCategory category, int year, String closing) {
        LeaveLedger ledger = new LeaveLedger();
        ledger.setId(500L + (category.getId() == null ? 0 : category.getId()));
        ledger.setUser(user);
        ledger.setCategory(category);
        ledger.setFiscalYear(year);
        ledger.setOpeningBalance(new BigDecimal(closing));
        ledger.setClosingBalance(new BigDecimal(closing));
        return ledger;
    }

    public static LeaveRequest leaveRequest(Long id, User user, LeaveCategory category,
                                            LocalDate start, LocalDate end, String totalDays,
                                            LeaveRequest.RequestStatus status) {
        LeaveRequest request = new LeaveRequest();
        request.setId(id);
        request.setUser(user);
        request.setCategory(category);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setTotalDays(new BigDecimal(totalDays));
        request.setStatus(status);
        request.setReason("Family function");
        request.setAppliedAt(LocalDateTime.of(2030, 1, 1, 10, 0));
        return request;
    }

    public static NotificationQueue notification(Long id, User user, NotificationQueue.Channel channel,
                                                 String templateCode, String payload) {
        NotificationQueue n = new NotificationQueue();
        n.setId(id);
        n.setUser(user);
        n.setChannel(channel);
        n.setTemplateCode(templateCode);
        n.setPayload(payload);
        n.setStatus(NotificationQueue.NotificationStatus.QUEUED);
        n.setCreatedAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        n.setScheduledAt(LocalDateTime.of(2030, 1, 1, 9, 0));
        n.setRetryCount(0);
        n.setIsRead(false);
        return n;
    }
}
