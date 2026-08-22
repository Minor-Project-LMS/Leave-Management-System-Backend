package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class LeaveRequestRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private User testUser;
    private LeaveCategory testCategory;
    private LeaveRequest testRequest;

    @BeforeEach
    void setUp() {
        // Create test role
        Role testRole = new Role();
        testRole.setId(1);
        testRole.setRoleCode("EMPLOYEE");
        testRole.setRoleDescription("Employee Role");
        entityManager.persist(testRole);

        // Create test user
        testUser = new User();
        testUser.setEmployeeCode("EMP001");
        testUser.setName("John Doe");
        testUser.setEmail("john.doe@example.com");
        testUser.setPasswordHash("hashedPassword");
        testUser.setRole(testRole);
        testUser.setDateOfJoining(LocalDate.of(2020, 1, 1));
        testUser.setEmploymentStatus(User.EmploymentStatus.ACTIVE);
        entityManager.persist(testUser);

        // Create test category
        testCategory = new LeaveCategory();
        testCategory.setName("Annual Leave");
        testCategory.setPaid(true);
        testCategory.setRequiresDocument(false);
        testCategory.setDefaultAnnualQuota(20.0);
        entityManager.persist(testCategory);

        // Create test leave request
        testRequest = new LeaveRequest();
        testRequest.setUser(testUser);
        testRequest.setCategory(testCategory);
        testRequest.setStartDate(LocalDate.of(2024, 1, 10));
        testRequest.setEndDate(LocalDate.of(2024, 1, 12));
        testRequest.setSessionType(LeaveRequest.SessionType.FULL_DAY);
        testRequest.setTotalDays(new BigDecimal("3.0"));
        testRequest.setReason("Vacation");
        testRequest.setStatus(LeaveRequest.RequestStatus.PENDING_L1);
        testRequest.setAppliedAt(java.time.LocalDateTime.now());
        entityManager.persist(testRequest);

        entityManager.flush();
    }

    @Test
    void findByUserId_WithExistingUser_ShouldReturnRequests() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByUserId(testUser.getId(), pageable);

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.getContent().size());
        assertEquals("Vacation", requests.getContent().get(0).getReason());
    }

    @Test
    void findByUserId_WithNonExistingUser_ShouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByUserId(999L, pageable);

        assertTrue(requests.isEmpty());
    }

    @Test
    void findByUserIdAndStatus_WithExistingUserAndStatus_ShouldReturnRequests() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByUserIdAndStatus(
                testUser.getId(),
                LeaveRequest.RequestStatus.PENDING_L1,
                pageable
        );

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.getContent().size());
        assertEquals(LeaveRequest.RequestStatus.PENDING_L1, requests.getContent().get(0).getStatus());
    }

    @Test
    void findByUserIdAndStatus_WithDifferentStatus_ShouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByUserIdAndStatus(
                testUser.getId(),
                LeaveRequest.RequestStatus.APPROVED,
                pageable
        );

        assertTrue(requests.isEmpty());
    }

    @Test
    void findByStatus_WithExistingStatus_ShouldReturnRequests() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByStatus(
                LeaveRequest.RequestStatus.PENDING_L1,
                pageable
        );

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.getContent().size());
    }

    @Test
    void findByStatus_WithNonExistingStatus_ShouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByStatus(
                LeaveRequest.RequestStatus.APPROVED,
                pageable
        );

        assertTrue(requests.isEmpty());
    }

    @Test
    void findByUserIdAndStatusAndStartDateBetween_WithMatchingDates_ShouldReturnRequests() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        List<LeaveRequest> requests = leaveRequestRepository.findByUserIdAndStatusAndStartDateBetween(
                testUser.getId(),
                LeaveRequest.RequestStatus.PENDING_L1,
                startDate,
                endDate
        );

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void findByUserIdAndStatusAndStartDateBetween_WithNonMatchingDates_ShouldReturnEmptyList() {
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 28);

        List<LeaveRequest> requests = leaveRequestRepository.findByUserIdAndStatusAndStartDateBetween(
                testUser.getId(),
                LeaveRequest.RequestStatus.PENDING_L1,
                startDate,
                endDate
        );

        assertTrue(requests.isEmpty());
    }

    @Test
    void findByCurrentApproverIdAndStatus_WithExistingApprover_ShouldReturnRequests() {
        // Set current approver
        testRequest.setCurrentApprover(testUser);
        entityManager.persist(testRequest);
        entityManager.flush();

        List<LeaveRequest> requests = leaveRequestRepository.findByCurrentApproverIdAndStatus(
                testUser.getId(),
                LeaveRequest.RequestStatus.PENDING_L1
        );

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void findByCurrentApproverId_WithExistingApprover_ShouldReturnPageOfRequests() {
        // Set current approver
        testRequest.setCurrentApprover(testUser);
        entityManager.persist(testRequest);
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findByCurrentApproverId(
                testUser.getId(),
                pageable
        );

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.getContent().size());
    }

    @Test
    void findAccessibleByUserId_WithOwnerUser_ShouldReturnRequests() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveRequest> requests = leaveRequestRepository.findAccessibleByUserId(
                testUser.getId(),
                pageable
        );

        assertFalse(requests.isEmpty());
        assertEquals(1, requests.getContent().size());
    }

    @Test
    void save_WithValidRequest_ShouldPersistRequest() {
        LeaveRequest newRequest = new LeaveRequest();
        newRequest.setUser(testUser);
        newRequest.setCategory(testCategory);
        newRequest.setStartDate(LocalDate.of(2024, 2, 1));
        newRequest.setEndDate(LocalDate.of(2024, 2, 2));
        newRequest.setSessionType(LeaveRequest.SessionType.FULL_DAY);
        newRequest.setTotalDays(new BigDecimal("2.0"));
        newRequest.setReason("Sick leave");
        newRequest.setStatus(LeaveRequest.RequestStatus.DRAFT);
        newRequest.setAppliedAt(java.time.LocalDateTime.now());

        LeaveRequest savedRequest = leaveRequestRepository.save(newRequest);

        assertNotNull(savedRequest.getId());
        assertEquals("Sick leave", savedRequest.getReason());
    }
}
