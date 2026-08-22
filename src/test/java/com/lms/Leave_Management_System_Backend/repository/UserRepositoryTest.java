package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.Role;
import com.lms.Leave_Management_System_Backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private Role testRole;
    private Department testDepartment;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Create test role
        testRole = new Role();
        testRole.setId(1);
        testRole.setRoleCode("EMPLOYEE");
        testRole.setRoleDescription("Employee Role");
        entityManager.persist(testRole);

        // Create test department
        testDepartment = new Department();
        testDepartment.setName("Engineering");
        entityManager.persist(testDepartment);

        // Create test user
        testUser = new User();
        testUser.setEmployeeCode("EMP001");
        testUser.setName("John Doe");
        testUser.setEmail("john.doe@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setRole(testRole);
        testUser.setDepartment(testDepartment);
        testUser.setDateOfJoining(java.time.LocalDate.of(2020, 1, 1));
        testUser.setEmploymentStatus(User.EmploymentStatus.ACTIVE);
        entityManager.persist(testUser);

        entityManager.flush();
    }

    @Test
    void findByEmailIgnoreCase_WithExistingEmail_ShouldReturnUser() {
        Optional<User> foundUser = userRepository.findByEmailIgnoreCase("john.doe@example.com");

        assertTrue(foundUser.isPresent());
        assertEquals("john.doe@example.com", foundUser.get().getEmail());
        assertEquals("John Doe", foundUser.get().getName());
    }

    @Test
    void findByEmailIgnoreCase_WithDifferentCase_ShouldReturnUser() {
        Optional<User> foundUser = userRepository.findByEmailIgnoreCase("JOHN.DOE@EXAMPLE.COM");

        assertTrue(foundUser.isPresent());
        assertEquals("john.doe@example.com", foundUser.get().getEmail());
    }

    @Test
    void findByEmailIgnoreCase_WithNonExistingEmail_ShouldReturnEmpty() {
        Optional<User> foundUser = userRepository.findByEmailIgnoreCase("nonexistent@example.com");

        assertFalse(foundUser.isPresent());
    }

    @Test
    void findByEmployeeCode_WithExistingCode_ShouldReturnUser() {
        Optional<User> foundUser = userRepository.findByEmployeeCode("EMP001");

        assertTrue(foundUser.isPresent());
        assertEquals("EMP001", foundUser.get().getEmployeeCode());
    }

    @Test
    void findByEmployeeCode_WithNonExistingCode_ShouldReturnEmpty() {
        Optional<User> foundUser = userRepository.findByEmployeeCode("NONEXISTENT");

        assertFalse(foundUser.isPresent());
    }

    @Test
    void findByDepartmentId_WithExistingDepartment_ShouldReturnUsers() {
        List<User> users = userRepository.findByDepartmentId(testDepartment.getId());

        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
        assertEquals("John Doe", users.get(0).getName());
    }

    @Test
    void findByDepartmentId_WithNonExistingDepartment_ShouldReturnEmptyList() {
        List<User> users = userRepository.findByDepartmentId(999);

        assertTrue(users.isEmpty());
    }

    @Test
    void findByReportsToId_WithExistingManager_ShouldReturnReportees() {
        // Create a manager user
        User manager = new User();
        manager.setEmployeeCode("MGR001");
        manager.setName("Manager User");
        manager.setEmail("manager@example.com");
        manager.setPasswordHash("hashedPassword");
        manager.setRole(testRole);
        manager.setDateOfJoining(java.time.LocalDate.of(2019, 1, 1));
        manager.setEmploymentStatus(User.EmploymentStatus.ACTIVE);
        entityManager.persist(manager);

        // Update test user to report to manager
        testUser.setReportsTo(manager);
        entityManager.persist(testUser);
        entityManager.flush();

        List<User> reportees = userRepository.findByReportsToId(manager.getId());

        assertFalse(reportees.isEmpty());
        assertEquals(1, reportees.size());
        assertEquals("John Doe", reportees.get(0).getName());
    }

    @Test
    void findActiveUsers_ShouldReturnOnlyActiveUsers() {
        // Create an inactive user
        User inactiveUser = new User();
        inactiveUser.setEmployeeCode("EMP002");
        inactiveUser.setName("Inactive User");
        inactiveUser.setEmail("inactive@example.com");
        inactiveUser.setPasswordHash("hashedPassword");
        inactiveUser.setRole(testRole);
        inactiveUser.setDateOfJoining(java.time.LocalDate.of(2020, 1, 1));
        inactiveUser.setEmploymentStatus(User.EmploymentStatus.SEPARATED);
        entityManager.persist(inactiveUser);
        entityManager.flush();

        List<User> activeUsers = userRepository.findActiveUsers();

        assertEquals(1, activeUsers.size());
        assertEquals("John Doe", activeUsers.get(0).getName());
        // Avoid lazy loading issue by checking only ID if department is loaded
        assertNotNull(activeUsers.get(0).getDepartment());
        assertEquals(testDepartment.getId(), activeUsers.get(0).getDepartment().getId());
    }

    @Test
    void findActiveUsersByDepartment_WithExistingDepartment_ShouldReturnActiveUsers() {
        List<User> activeUsers = userRepository.findActiveUsersByDepartment(testDepartment.getId());

        assertEquals(1, activeUsers.size());
        assertEquals("John Doe", activeUsers.get(0).getName());
        // Avoid lazy loading in test by checking only ID
        assertEquals(testDepartment.getId(), activeUsers.get(0).getDepartment().getId());
    }

    @Test
    void findByEmploymentStatus_WithActiveStatus_ShouldReturnActiveUsers() {
        List<User> activeUsers = userRepository.findByEmploymentStatus(User.EmploymentStatus.ACTIVE);

        assertEquals(1, activeUsers.size());
        assertEquals("John Doe", activeUsers.get(0).getName());
    }
}
