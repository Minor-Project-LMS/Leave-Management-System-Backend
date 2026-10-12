package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.AttachmentDto;
import com.lms.Leave_Management_System_Backend.dto.AttachmentInitUploadResponse;
import com.lms.Leave_Management_System_Backend.model.*;
import com.lms.Leave_Management_System_Backend.repository.*;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompOffControllerTest {

    private CompOffRequestRepository compOffs;
    private UserRepository users;
    private LeaveLedgerRepository ledgers;
    private LeaveCategoryRepository categories;
    private NotificationQueueRepository notifications;
    private AttachmentService attachments;
    private LeaveRequestRepository leaveRequests;
    private ApprovalDelegationRepository delegations;
    private MockMvc mvc;

    private User manager;
    private User hrUser;
    private User report;
    private User stranger;
    private LeaveCategory compOffCategory;

    @BeforeEach
    void setUp() {
        compOffs = mock(CompOffRequestRepository.class);
        users = mock(UserRepository.class);
        ledgers = mock(LeaveLedgerRepository.class);
        categories = mock(LeaveCategoryRepository.class);
        notifications = mock(NotificationQueueRepository.class);
        attachments = mock(AttachmentService.class);
        leaveRequests = mock(LeaveRequestRepository.class);
        delegations = mock(ApprovalDelegationRepository.class);
        mvc = mockMvc(new CompOffController(compOffs, users, ledgers, categories, notifications,
                attachments, leaveRequests, delegations));

        manager = manager(2L, "mgr@lms.com");
        hrUser = hr(3L, "hr@lms.com");
        report = employee(1L, "emp@lms.com");
        report.setReportsTo(manager);
        stranger = employee(5L, "stranger@lms.com");
        compOffCategory = category(6, "Comp Off");
        compOffCategory.setCategoryCode("COMP_OFF");

        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager));
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hrUser));
        when(users.findByEmailIgnoreCase("emp@lms.com")).thenReturn(Optional.of(report));
        when(users.findByEmailIgnoreCase("stranger@lms.com")).thenReturn(Optional.of(stranger));
        when(users.findById(1L)).thenReturn(Optional.of(report));
        when(users.findById(5L)).thenReturn(Optional.of(stranger));
        when(categories.findByCategoryCode("COMP_OFF")).thenReturn(Optional.of(compOffCategory));
        when(compOffs.save(any(CompOffRequest.class))).thenAnswer(inv -> {
            CompOffRequest c = inv.getArgument(0);
            if (c.getId() == null) c.setId(14L);
            return c;
        });
    }

    private String grant(long userId, double hours) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("workedOn", "2030-01-05");
        body.put("reason", "Weekend release");
        body.put("hoursWorked", hours);
        body.put("expiryDate", "2030-03-31");
        return json(body);
    }

    private CompOffRequest grantFor(User user, CompOffRequest.RequestStatus status) {
        CompOffRequest c = new CompOffRequest();
        c.setId(14L);
        c.setUser(user);
        c.setWorkedOn(LocalDate.of(2030, 1, 5));
        c.setDaysCredited(BigDecimal.ONE);
        c.setExpiryDate(LocalDate.of(2030, 3, 31));
        c.setStatus(status);
        c.setCreatedAt(LocalDateTime.of(2030, 1, 6, 9, 0));
        c.setIssuer(manager);
        return c;
    }

    @Nested
    class Grant {

        @Test
        @DisplayName("manager grants a full day for 8+ hours; ledger is credited and employee notified")
        void managerGrantsFullDay() throws Exception {
            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("mgr@lms.com", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(grant(1L, 9)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.displayId").value("CO-" + LocalDate.now().getYear() + "-014"))
                    .andExpect(jsonPath("$.daysCredited").value(1.0))
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.issuerId").value(2))
                    .andExpect(jsonPath("$.daysRemaining").value(1.0));

            ArgumentCaptor<LeaveLedger> ledger = ArgumentCaptor.forClass(LeaveLedger.class);
            verify(ledgers).save(ledger.capture());
            assertThat(ledger.getValue().getFiscalYear()).isEqualTo(2030);
            assertThat(ledger.getValue().getAccrued()).isEqualByComparingTo("1.0");
            assertThat(ledger.getValue().getClosingBalance()).isEqualByComparingTo("1.0");
            assertThat(ledger.getValue().getReferenceType()).isEqualTo("COMP_OFF_REQUEST");
            assertThat(ledger.getValue().getReferenceId()).isEqualTo(14L);
            verify(notifications, times(2)).save(any(NotificationQueue.class));
        }

        @Test
        void lessThan8HoursIsHalfDayAndAddsToExistingLedger() throws Exception {
            LeaveLedger existing = ledger(report, compOffCategory, 2030, "1");
            existing.setAccrued(BigDecimal.ONE);
            when(ledgers.findByUserIdAndCategoryIdAndFiscalYear(1L, 6, 2030)).thenReturn(Optional.of(existing));

            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(grant(1L, 4)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.daysCredited").value(0.5));

            assertThat(existing.getAccrued()).isEqualByComparingTo("1.5");
            assertThat(existing.getClosingBalance()).isEqualByComparingTo("1.5");
        }

        @Test
        void categoryFoundByNameWhenCodeMissing() throws Exception {
            when(categories.findByCategoryCode("COMP_OFF")).thenReturn(Optional.empty());
            LeaveCategory byName = category(7, "Compensatory Off");
            when(categories.findAll()).thenReturn(List.of(category(1, "Casual Leave"), byName));

            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(grant(1L, 8)))
                    .andExpect(status().isCreated());
            verify(ledgers).findByUserIdAndCategoryIdAndFiscalYear(1L, 7, 2030);
        }

        @Test
        void missingCompOffCategoryIs404() throws Exception {
            when(categories.findByCategoryCode("COMP_OFF")).thenReturn(Optional.empty());
            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(grant(1L, 8)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void managerCannotGrantToSomeoneElsesReport() throws Exception {
            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("mgr@lms.com", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(grant(5L, 8)))
                    .andExpect(status().isUnauthorized());
            verify(compOffs, never()).save(any());
        }

        @Test
        @DisplayName("an active delegate of the employee's manager can grant")
        void delegateCanGrant() throws Exception {
            User otherManager = manager(8L, "other@lms.com");
            stranger.setReportsTo(otherManager);
            ApprovalDelegation delegation = new ApprovalDelegation();
            delegation.setDelegator(manager);
            delegation.setDelegate(otherManager);
            when(delegations.findActiveDelegationsForDelegatorOnDate(eq(2L), any())).thenReturn(List.of(delegation));

            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("mgr@lms.com", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(grant(5L, 8)))
                    .andExpect(status().isCreated());
        }

        @Test
        void validationErrors() throws Exception {
            mvc.perform(post("/api/v1/comp-off-requests").principal(auth("mgr@lms.com", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    class ListGrants {

        @Test
        void employeeSeesOwnByDefault() throws Exception {
            when(compOffs.findByUserId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(grantFor(report, CompOffRequest.RequestStatus.APPROVED))));
            when(leaveRequests.sumDaysClaimedByCompOffRequestId(14L)).thenReturn(0.5);

            mvc.perform(get("/api/v1/comp-off-requests").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].daysClaimed").value(0.5))
                    .andExpect(jsonPath("$.data[0].daysRemaining").value(0.5));
        }

        @Test
        void statusAndBalanceFilters() throws Exception {
            when(compOffs.findByUserIdAndStatus(eq(1L), eq(CompOffRequest.RequestStatus.APPROVED), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(compOffs.findWithFiltersAndBalance(eq(1L), isNull(), eq(true), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/api/v1/comp-off-requests").param("status", "APPROVED").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/comp-off-requests").param("hasBalance", "true").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk());

            verify(compOffs).findByUserIdAndStatus(eq(1L), eq(CompOffRequest.RequestStatus.APPROVED), any(Pageable.class));
            verify(compOffs).findWithFiltersAndBalance(eq(1L), isNull(), eq(true), any(), any(Pageable.class));
        }

        @Test
        void employeeCannotViewSomeoneElse() throws Exception {
            mvc.perform(get("/api/v1/comp-off-requests").param("userId", "5").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void managerCanViewDirectReportOnly() throws Exception {
            when(compOffs.findByUserId(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
            mvc.perform(get("/api/v1/comp-off-requests").param("userId", "1").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/comp-off-requests").param("userId", "5").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Detail {

        @Test
        void ownerSeesLinkedRequestsAndAttachments() throws Exception {
            when(compOffs.findById(14L)).thenReturn(Optional.of(grantFor(report, CompOffRequest.RequestStatus.APPROVED)));
            LeaveRequest claim = leaveRequest(30L, report, compOffCategory, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 1),
                    "1", LeaveRequest.RequestStatus.PENDING_L1);
            when(leaveRequests.findByCompOffRequestId(14L)).thenReturn(List.of(claim));
            when(attachments.listAttachments(Attachment.EntityType.COMP_OFF_REQUEST, 14L)).thenReturn(List.of(new AttachmentDto()));

            mvc.perform(get("/api/v1/comp-off-requests/14").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayId").value("CO-2030-014"))
                    .andExpect(jsonPath("$.linkedLeaveRequests[0].id").value(30))
                    .andExpect(jsonPath("$.attachments.length()").value(1));
        }

        @Test
        void strangerIsBlocked() throws Exception {
            when(compOffs.findById(14L)).thenReturn(Optional.of(grantFor(report, CompOffRequest.RequestStatus.APPROVED)));
            mvc.perform(get("/api/v1/comp-off-requests/14").principal(auth("stranger@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void missingIs404() throws Exception {
            mvc.perform(get("/api/v1/comp-off-requests/99").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Revoke {

        @Test
        void issuerRevokesUnusedGrant() throws Exception {
            CompOffRequest c = grantFor(report, CompOffRequest.RequestStatus.APPROVED);
            when(compOffs.findById(14L)).thenReturn(Optional.of(c));

            mvc.perform(delete("/api/v1/comp-off-requests/14").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isNoContent());
            assertThat(c.getStatus()).isEqualTo(CompOffRequest.RequestStatus.REJECTED);
            verify(notifications, times(2)).save(any(NotificationQueue.class));
        }

        @Test
        void cannotRevokeWithActiveClaims() throws Exception {
            when(compOffs.findById(14L)).thenReturn(Optional.of(grantFor(report, CompOffRequest.RequestStatus.APPROVED)));
            when(leaveRequests.findByCompOffRequestId(14L)).thenReturn(List.of(leaveRequest(30L, report, compOffCategory,
                    LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 1), "1", LeaveRequest.RequestStatus.APPROVED)));

            mvc.perform(delete("/api/v1/comp-off-requests/14").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isConflict());
        }

        @Test
        void onlyApprovedGrantsCanBeRevoked() throws Exception {
            when(compOffs.findById(14L)).thenReturn(Optional.of(grantFor(report, CompOffRequest.RequestStatus.EXPIRED)));
            mvc.perform(delete("/api/v1/comp-off-requests/14").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isConflict());
        }

        @Test
        void unrelatedManagerCannotRevoke() throws Exception {
            CompOffRequest c = grantFor(stranger, CompOffRequest.RequestStatus.APPROVED);
            c.setIssuer(hrUser);
            when(compOffs.findById(14L)).thenReturn(Optional.of(c));

            mvc.perform(delete("/api/v1/comp-off-requests/14").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Attachments {

        @Test
        void ownerCanUploadAndConfirm() throws Exception {
            when(compOffs.findById(14L)).thenReturn(Optional.of(grantFor(report, CompOffRequest.RequestStatus.APPROVED)));
            when(attachments.initializeUpload(eq(Attachment.EntityType.COMP_OFF_REQUEST), eq(14L), any(), eq(1L)))
                    .thenReturn(new AttachmentInitUploadResponse(42L, "https://s3/put", "k"));
            when(attachments.confirmUpload(eq(42L), any())).thenReturn(new AttachmentDto());

            mvc.perform(post("/api/v1/comp-off-requests/14/attachments/init-upload").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("fileName", "proof.pdf", "contentType", "application/pdf", "sizeBytes", 100))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.attachmentId").value(42));
            mvc.perform(post("/api/v1/comp-off-requests/14/attachments/42/confirm").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void nonOwnerCannotUpload() throws Exception {
            when(compOffs.findById(14L)).thenReturn(Optional.of(grantFor(report, CompOffRequest.RequestStatus.APPROVED)));
            mvc.perform(post("/api/v1/comp-off-requests/14/attachments/init-upload").principal(auth("stranger@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("fileName", "x.pdf", "contentType", "application/pdf", "sizeBytes", 100))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/comp-off-requests/14/attachments/42/confirm").principal(auth("stranger@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void viewingRules() throws Exception {
            CompOffRequest c = grantFor(report, CompOffRequest.RequestStatus.APPROVED);
            when(compOffs.findById(14L)).thenReturn(Optional.of(c));
            when(attachments.getAttachment(42L)).thenReturn(new AttachmentDto());

            mvc.perform(get("/api/v1/comp-off-requests/14/attachments").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/comp-off-requests/14/attachments").principal(auth("stranger@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
            // a manager must be the recorded approver (none set here)
            mvc.perform(get("/api/v1/comp-off-requests/14/attachments/42").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isUnauthorized());
            c.setApprover(manager);
            mvc.perform(get("/api/v1/comp-off-requests/14/attachments/42").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/comp-off-requests/14/attachments/42").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isOk());
        }
    }
}
