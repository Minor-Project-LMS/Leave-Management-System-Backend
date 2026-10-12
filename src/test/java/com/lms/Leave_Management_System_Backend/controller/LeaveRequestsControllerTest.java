package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.AttachmentDto;
import com.lms.Leave_Management_System_Backend.dto.AttachmentInitUploadResponse;
import com.lms.Leave_Management_System_Backend.model.*;
import com.lms.Leave_Management_System_Backend.repository.*;
import com.lms.Leave_Management_System_Backend.service.AttachmentService;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import com.lms.Leave_Management_System_Backend.service.WorkWeekService;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Leave request lifecycle: apply (sandwich day counting, LOP, overlap,
 * policy limits, comp-off claims, handover checklist), list/view, edit and
 * submit drafts, approve/reject (L1 -> L2 escalation, ledger deduction,
 * delegated approvers), withdraw, comments and attachments.
 *
 * Calendar used (2030): Wed 2 Jan, Fri 4 Jan, Sat 5 Jan, Sun 6 Jan, Mon 7 Jan.
 */
class LeaveRequestsControllerTest {

    private static final LocalDate WED = LocalDate.of(2030, 1, 2);
    private static final LocalDate FRI = LocalDate.of(2030, 1, 4);
    private static final LocalDate SAT = LocalDate.of(2030, 1, 5);
    private static final LocalDate MON = LocalDate.of(2030, 1, 7);

    private LeaveRequestRepository requests;
    private UserRepository users;
    private LeaveCategoryRepository categories;
    private LeaveApprovalRepository approvals;
    private ApprovalDelegationRepository delegations;
    private LeaveLedgerRepository ledgers;
    private NotificationQueueRepository notifications;
    private LeavePolicyRepository policies;
    private AttachmentService attachments;
    private LeaveLedgerProvisioningService provisioning;
    private CompOffRequestRepository compOffs;
    private WorkWeekService workWeek;
    private HandoverTaskRepository handoverTasks;
    private MockMvc mvc;

    private User me;
    private User manager;
    private User hrUser;
    private User other;
    private LeaveCategory casual;
    private LeaveLedger myLedger;

    @BeforeEach
    void setUp() {
        requests = mock(LeaveRequestRepository.class);
        users = mock(UserRepository.class);
        categories = mock(LeaveCategoryRepository.class);
        approvals = mock(LeaveApprovalRepository.class);
        delegations = mock(ApprovalDelegationRepository.class);
        ledgers = mock(LeaveLedgerRepository.class);
        notifications = mock(NotificationQueueRepository.class);
        policies = mock(LeavePolicyRepository.class);
        attachments = mock(AttachmentService.class);
        provisioning = mock(LeaveLedgerProvisioningService.class);
        compOffs = mock(CompOffRequestRepository.class);
        workWeek = mock(WorkWeekService.class);
        handoverTasks = mock(HandoverTaskRepository.class);
        mvc = mockMvc(new LeaveRequestsController(requests, users, categories, approvals, delegations, ledgers,
                notifications, policies, attachments, provisioning, compOffs, workWeek, handoverTasks));

        manager = manager(2L, "mgr@lms.com");
        hrUser = hr(3L, "hr@lms.com");
        me = employee(1L, "emp@lms.com");
        me.setDepartment(department(10, "Engineering"));
        me.setReportsTo(manager);
        other = employee(5L, "other@lms.com");
        casual = category(1, "Casual Leave");
        myLedger = ledger(me, casual, LocalDate.now().getYear(), "10");

        when(users.findByEmailIgnoreCase("emp@lms.com")).thenReturn(Optional.of(me));
        when(users.findByEmailIgnoreCase("mgr@lms.com")).thenReturn(Optional.of(manager));
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hrUser));
        when(users.findByEmailIgnoreCase("other@lms.com")).thenReturn(Optional.of(other));
        when(categories.findById(1)).thenReturn(Optional.of(casual));
        when(ledgers.findByUserIdAndCategoryIdAndFiscalYear(eq(1L), eq(1), anyInt())).thenReturn(Optional.of(myLedger));
        when(workWeek.isWorkingDay(any(LocalDate.class))).thenAnswer(inv -> {
            DayOfWeek d = inv.<LocalDate>getArgument(0).getDayOfWeek();
            return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
        });
        when(requests.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(100L);
            return r;
        });
    }

    private Map<String, Object> body(LocalDate start, LocalDate end) {
        Map<String, Object> b = new HashMap<>();
        b.put("categoryId", 1);
        b.put("startDate", start.toString());
        b.put("endDate", end.toString());
        b.put("sessionType", "FULL_DAY");
        b.put("reason", "Family function");
        return b;
    }

    private org.springframework.test.web.servlet.ResultActions apply(Map<String, Object> body, String email) throws Exception {
        return mvc.perform(post("/api/v1/leave-requests").principal(auth(email, "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private LeaveRequest existing(Long id, User owner, LeaveRequest.RequestStatus status, String days) {
        LeaveRequest r = leaveRequest(id, owner, casual, FRI, MON, days, status);
        when(requests.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    // =====================================================================
    @Nested
    class Apply {

        @Test
        @DisplayName("Fri->Mon counts 4 days (weekend sandwiched); LOP = days beyond balance; routed to manager")
        void sandwichAndLop() throws Exception {
            myLedger.setClosingBalance(new BigDecimal("2"));

            apply(body(FRI, MON), "emp@lms.com")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.totalDays").value(4))
                    .andExpect(jsonPath("$.data.lopDays").value(2))
                    .andExpect(jsonPath("$.data.status").value("PENDING_L1"))
                    .andExpect(jsonPath("$.data.currentApproverId").value(2));

            verify(provisioning).getOrInitializeLedger(eq(me), anyInt());
            // employee (IN_APP + EMAIL) and manager (IN_APP + EMAIL)
            ArgumentCaptor<NotificationQueue> n = ArgumentCaptor.forClass(NotificationQueue.class);
            verify(notifications, times(4)).save(n.capture());
            assertThat(n.getAllValues()).extracting(NotificationQueue::getTemplateCode)
                    .containsExactly("LEAVE_SUBMITTED", "LEAVE_SUBMITTED", "LEAVE_APPROVAL_PENDING", "LEAVE_APPROVAL_PENDING");
        }

        @Test
        @DisplayName("a non-working day at the edge of the range is not charged (Sat->Mon = 2)")
        void boundaryWeekendNotCharged() throws Exception {
            apply(body(SAT, MON), "emp@lms.com")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.totalDays").value(2))
                    .andExpect(jsonPath("$.data.lopDays").value(0));
        }

        @Test
        void halfDayCountsHalf() throws Exception {
            Map<String, Object> b = body(WED, WED);
            b.put("sessionType", "FIRST_HALF");
            apply(b, "emp@lms.com")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.totalDays").value(0.5))
                    .andExpect(jsonPath("$.data.sessionType").value("FIRST_HALF"));
        }

        @Test
        @DisplayName("DRAFT skips balance/overlap checks and has no approver")
        void draft() throws Exception {
            Map<String, Object> b = body(FRI, MON);
            b.put("status", "DRAFT");
            apply(b, "emp@lms.com")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.currentApproverId").doesNotExist());
            verifyNoInteractions(provisioning);
        }

        @Test
        void activeDelegationRoutesToDelegate() throws Exception {
            ApprovalDelegation d = new ApprovalDelegation();
            d.setDelegator(manager);
            d.setDelegate(hrUser);
            when(delegations.findActiveDelegationsForDelegatorOnDate(eq(2L), any())).thenReturn(List.of(d));

            apply(body(FRI, MON), "emp@lms.com")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.currentApproverId").value(3));
        }

        @Test
        void overlappingPendingRequestIs409() throws Exception {
            LeaveRequest pending = leaveRequest(50L, me, casual, MON, MON.plusDays(1), "2", LeaveRequest.RequestStatus.PENDING_L1);
            when(requests.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(pending));

            apply(body(FRI, MON), "emp@lms.com")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value(containsString("pending approval")));
            verify(requests, never()).save(any());
        }

        @Test
        void overlappingApprovedRequestIs409() throws Exception {
            LeaveRequest approved = leaveRequest(51L, me, casual, FRI, FRI, "1", LeaveRequest.RequestStatus.APPROVED);
            when(requests.findByUserIdAndStatus(1L, LeaveRequest.RequestStatus.APPROVED)).thenReturn(List.of(approved));

            apply(body(FRI, MON), "emp@lms.com")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("Your leave request is already approved for this date."));
        }

        @Test
        void nonOverlappingApprovedRequestIsFine() throws Exception {
            LeaveRequest approved = leaveRequest(51L, me, casual, WED, WED, "1", LeaveRequest.RequestStatus.APPROVED);
            when(requests.findByUserIdAndStatus(1L, LeaveRequest.RequestStatus.APPROVED)).thenReturn(List.of(approved));

            apply(body(FRI, MON), "emp@lms.com").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("department policy max-consecutive-days is enforced")
        void maxConsecutiveDays() throws Exception {
            LeavePolicy policy = new LeavePolicy();
            policy.setDepartment(department(10, "Engineering"));
            policy.setMaxConsecutiveDays(3);
            when(policies.findByCategoryId(1)).thenReturn(List.of(policy));

            apply(body(FRI, MON), "emp@lms.com")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value(containsString("exceeds the maximum allowed continuous limit of 3")));
        }

        @Test
        void orgWidePolicyAppliesToUserWithoutDepartment() throws Exception {
            me.setDepartment(null);
            LeavePolicy policy = new LeavePolicy();
            policy.setMaxConsecutiveDays(2);
            when(policies.findByCategoryIdAndDepartmentIdIsNull(1)).thenReturn(List.of(policy));

            apply(body(FRI, MON), "emp@lms.com").andExpect(status().isConflict());
        }

        @Test
        void otherDepartmentsPolicyIsIgnored() throws Exception {
            LeavePolicy policy = new LeavePolicy();
            policy.setDepartment(department(99, "Sales"));
            policy.setMaxConsecutiveDays(1);
            when(policies.findByCategoryId(1)).thenReturn(List.of(policy));

            apply(body(FRI, MON), "emp@lms.com").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("handover checklist: blank rows skipped, bad urgency falls back to MEDIUM")
        void handoverTasks() throws Exception {
            Map<String, Object> b = body(FRI, MON);
            b.put("handoverTo", 5);
            b.put("handoverTasks", List.of(
                    Map.of("description", "  Deploy release ", "urgency", "high"),
                    Map.of("description", "   "),
                    Map.of("description", "Review PRs", "urgency", "whenever")));
            when(users.findById(5L)).thenReturn(Optional.of(other));

            apply(b, "emp@lms.com").andExpect(status().isCreated());

            ArgumentCaptor<HandoverTask> saved = ArgumentCaptor.forClass(HandoverTask.class);
            verify(handoverTasks, times(2)).save(saved.capture());
            assertThat(saved.getAllValues()).extracting(HandoverTask::getDescription).containsExactly("Deploy release", "Review PRs");
            assertThat(saved.getAllValues()).extracting(HandoverTask::getUrgency)
                    .containsExactly(HandoverTask.Urgency.HIGH, HandoverTask.Urgency.MEDIUM);
        }

        @Test
        void unknownHandoverUserIs404() throws Exception {
            Map<String, Object> b = body(FRI, MON);
            b.put("handoverTo", 77);
            apply(b, "emp@lms.com").andExpect(status().isNotFound());
        }

        @Test
        void unknownCategoryIs404() throws Exception {
            Map<String, Object> b = body(FRI, MON);
            b.put("categoryId", 42);
            apply(b, "emp@lms.com").andExpect(status().isNotFound());
        }

        @Test
        void missingReasonFailsValidation() throws Exception {
            Map<String, Object> b = body(FRI, MON);
            b.remove("reason");
            apply(b, "emp@lms.com")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // =====================================================================
    @Nested
    class CompOffClaims {

        private LeaveCategory compOffCategory;
        private CompOffRequest grant;

        @BeforeEach
        void setUp() {
            compOffCategory = category(6, "Comp Off");
            compOffCategory.setCategoryCode("COMP_OFF");
            when(categories.findById(6)).thenReturn(Optional.of(compOffCategory));
            when(ledgers.findByUserIdAndCategoryIdAndFiscalYear(eq(1L), eq(6), anyInt())).thenReturn(Optional.empty());
            grant = new CompOffRequest();
            grant.setId(14L);
            grant.setUser(me);
            grant.setStatus(CompOffRequest.RequestStatus.APPROVED);
            grant.setDaysCredited(BigDecimal.ONE);
            grant.setExpiryDate(LocalDate.of(2030, 3, 31));
            when(compOffs.findById(14L)).thenReturn(Optional.of(grant));
        }

        private Map<String, Object> claim(int categoryId, LocalDate day) {
            Map<String, Object> b = body(day, day);
            b.put("categoryId", categoryId);
            b.put("compOffRequestId", 14);
            return b;
        }

        @Test
        void validClaim() throws Exception {
            apply(claim(6, WED), "emp@lms.com").andExpect(status().isCreated());
        }

        @Test
        void grantOfAnotherEmployeeIs401() throws Exception {
            grant.setUser(other);
            apply(claim(6, WED), "emp@lms.com").andExpect(status().isUnauthorized());
        }

        @Test
        void grantNotApprovedIs409() throws Exception {
            grant.setStatus(CompOffRequest.RequestStatus.EXPIRED);
            apply(claim(6, WED), "emp@lms.com").andExpect(status().isConflict());
        }

        @Test
        void claimMustUseCompOffCategory() throws Exception {
            apply(claim(1, WED), "emp@lms.com")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value(containsString("Comp Off leave category")));
        }

        @Test
        void claimAfterExpiryIs409() throws Exception {
            apply(claim(6, LocalDate.of(2030, 4, 2)), "emp@lms.com")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value(containsString("expiry date")));
        }

        @Test
        void unknownGrantIs404() throws Exception {
            Map<String, Object> b = claim(6, WED);
            b.put("compOffRequestId", 99);
            apply(b, "emp@lms.com").andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    @Nested
    class ListAndView {

        @Test
        void employeeSeesOnlyOwnRequests() throws Exception {
            when(requests.findByUserIdAndStatus(eq(1L), eq(LeaveRequest.RequestStatus.PENDING_L1), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(leaveRequest(1L, me, casual, FRI, MON, "4", LeaveRequest.RequestStatus.PENDING_L1))));

            mvc.perform(get("/api/v1/leave-requests").param("status", "PENDING_L1").param("userId", "5")
                            .principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].userId").value(1));
            verify(requests, never()).findByUserIdAndStatus(eq(5L), any(), any(Pageable.class));
        }

        @Test
        void employeeWithoutStatus() throws Exception {
            when(requests.findByUserId(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
            mvc.perform(get("/api/v1/leave-requests").principal(auth("emp@lms.com", "EMPLOYEE"))).andExpect(status().isOk());
            verify(requests).findByUserId(eq(1L), any(Pageable.class));
        }

        @Test
        void hrFilterCombinations() throws Exception {
            when(requests.findByUserId(eq(5L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
            when(requests.findByUserIdAndStatus(eq(5L), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
            when(requests.findByStatus(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
            when(requests.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/api/v1/leave-requests").param("userId", "5").principal(auth("hr@lms.com", "HR_ADMIN"))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/leave-requests").param("userId", "5").param("status", "APPROVED")
                    .principal(auth("hr@lms.com", "HR_ADMIN"))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/leave-requests").param("status", "REJECTED").param("sort", "oldest")
                    .principal(auth("hr@lms.com", "HR_ADMIN"))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/leave-requests").param("sort", "startDate,desc")
                    .principal(auth("hr@lms.com", "HR_ADMIN"))).andExpect(status().isOk());

            verify(requests).findByUserId(eq(5L), any(Pageable.class));
            verify(requests).findByUserIdAndStatus(eq(5L), eq(LeaveRequest.RequestStatus.APPROVED), any(Pageable.class));
            verify(requests).findByStatus(eq(LeaveRequest.RequestStatus.REJECTED), any(Pageable.class));
            verify(requests).findAll(any(Pageable.class));
        }

        @Test
        void invalidStatusIs400() throws Exception {
            mvc.perform(get("/api/v1/leave-requests").param("status", "NOPE").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void viewRules() throws Exception {
            LeaveRequest r = existing(10L, me, LeaveRequest.RequestStatus.PENDING_L1, "4");
            HandoverTask task = new HandoverTask();
            task.setId(1L);
            task.setDescription("Deploy");
            task.setUrgency(HandoverTask.Urgency.HIGH);
            when(handoverTasks.findByLeaveRequestIdOrderByIdAsc(10L)).thenReturn(List.of(task));
            r.setEscalated(true);
            r.setOriginalApprover(manager);

            mvc.perform(get("/api/v1/leave-requests/10").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.handoverTasks[0].urgency").value("HIGH"))
                    .andExpect(jsonPath("$.data.escalated").value(true))
                    .andExpect(jsonPath("$.data.originalApproverName").value("Manager 2"));
            mvc.perform(get("/api/v1/leave-requests/10").principal(auth("other@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/leave-requests/10").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/leave-requests/999").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    @Nested
    class Drafts {

        @Test
        void editDraftRecalculatesDays() throws Exception {
            LeaveRequest draft = existing(20L, me, LeaveRequest.RequestStatus.DRAFT, "4");
            draft.setHandoverTo(other);

            mvc.perform(patch("/api/v1/leave-requests/20").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body(WED, FRI))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalDays").value(3));
            assertThat(draft.getHandoverTo()).isNull();
        }

        @Test
        void onlyDraftsCanBeEdited() throws Exception {
            existing(21L, me, LeaveRequest.RequestStatus.PENDING_L1, "4");
            mvc.perform(patch("/api/v1/leave-requests/21").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body(WED, FRI))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("Only draft requests can be edited"));
        }

        @Test
        void cannotEditSomeoneElsesDraft() throws Exception {
            existing(22L, other, LeaveRequest.RequestStatus.DRAFT, "4");
            mvc.perform(patch("/api/v1/leave-requests/22").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body(WED, FRI))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void submitDraftRoutesToManagerAndComputesLop() throws Exception {
            existing(23L, me, LeaveRequest.RequestStatus.DRAFT, "4");
            myLedger.setClosingBalance(new BigDecimal("3"));

            mvc.perform(post("/api/v1/leave-requests/23/submit").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING_L1"))
                    .andExpect(jsonPath("$.data.currentApproverId").value(2))
                    .andExpect(jsonPath("$.data.lopDays").value(1));
        }

        @Test
        @DisplayName("a draft does not conflict with itself when submitted")
        void submitIgnoresItselfInOverlapCheck() throws Exception {
            LeaveRequest draft = existing(24L, me, LeaveRequest.RequestStatus.DRAFT, "4");
            LeaveRequest sameIdPending = leaveRequest(24L, me, casual, FRI, MON, "4", LeaveRequest.RequestStatus.PENDING_L1);
            when(requests.findByUserIdAndStatusIn(eq(1L), anyList())).thenReturn(List.of(sameIdPending));

            mvc.perform(post("/api/v1/leave-requests/24/submit").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk());
            assertThat(draft.getStatus()).isEqualTo(LeaveRequest.RequestStatus.PENDING_L1);
        }

        @Test
        void submitRules() throws Exception {
            existing(25L, me, LeaveRequest.RequestStatus.APPROVED, "4");
            existing(26L, other, LeaveRequest.RequestStatus.DRAFT, "4");

            mvc.perform(post("/api/v1/leave-requests/25/submit").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/v1/leave-requests/26/submit").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =====================================================================
    @Nested
    class Decisions {

        private org.springframework.test.web.servlet.ResultActions decide(Long id, String email, String decision, String comments)
                throws Exception {
            Map<String, Object> b = new HashMap<>();
            b.put("decision", decision);
            if (comments != null) b.put("comments", comments);
            return mvc.perform(patch("/api/v1/leave-requests/" + id + "/decisions").principal(auth(email, "MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON).content(json(b)));
        }

        private LeaveRequest pendingWithManager(Long id, String days) {
            LeaveRequest r = existing(id, me, LeaveRequest.RequestStatus.PENDING_L1, days);
            r.setCurrentApprover(manager);
            return r;
        }

        @Test
        @DisplayName("final approval deducts the ledger and records a level-1 approval")
        void approveDeductsLedger() throws Exception {
            LeaveRequest r = pendingWithManager(30L, "3");

            decide(30L, "mgr@lms.com", "APPROVED", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.currentApproverId").doesNotExist());

            assertThat(myLedger.getUsed()).isEqualByComparingTo("3");
            assertThat(myLedger.getClosingBalance()).isEqualByComparingTo("7");
            assertThat(r.getLopDays()).isEqualByComparingTo("0");
            ArgumentCaptor<LeaveApproval> approval = ArgumentCaptor.forClass(LeaveApproval.class);
            verify(approvals).save(approval.capture());
            assertThat(approval.getValue().getLevel()).isEqualTo((short) 1);
            assertThat(approval.getValue().getDecision()).isEqualTo(LeaveApproval.Decision.APPROVED);
            verify(provisioning).getOrInitializeLedger(eq(me), anyInt());
        }

        @Test
        @DisplayName("balance is re-checked at approval: shortfall becomes LOP, only paid days are deducted")
        void approveWithShortBalance() throws Exception {
            LeaveRequest r = pendingWithManager(31L, "3");
            myLedger.setClosingBalance(new BigDecimal("1"));

            decide(31L, "mgr@lms.com", "APPROVED", null).andExpect(status().isOk());

            assertThat(r.getLopDays()).isEqualByComparingTo("2");
            assertThat(myLedger.getUsed()).isEqualByComparingTo("1");
            assertThat(myLedger.getClosingBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("more than 5 days at L1 escalates to HR (PENDING_L2) instead of approving")
        void longLeaveGoesToHr() throws Exception {
            pendingWithManager(32L, "6");
            when(users.findFirstByRole_RoleCode("HR_ADMIN")).thenReturn(Optional.of(hrUser));

            decide(32L, "mgr@lms.com", "APPROVED", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING_L2"))
                    .andExpect(jsonPath("$.data.currentApproverId").value(3));
            verify(ledgers, never()).save(any());
        }

        @Test
        void hrFinalApprovalAtL2() throws Exception {
            LeaveRequest r = existing(33L, me, LeaveRequest.RequestStatus.PENDING_L2, "6");
            r.setCurrentApprover(hrUser);

            decide(33L, "hr@lms.com", "APPROVED", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
            ArgumentCaptor<LeaveApproval> approval = ArgumentCaptor.forClass(LeaveApproval.class);
            verify(approvals).save(approval.capture());
            assertThat(approval.getValue().getLevel()).isEqualTo((short) 2);
        }

        @Test
        void noHrAdminForEscalationIs404() throws Exception {
            pendingWithManager(34L, "6");
            decide(34L, "mgr@lms.com", "APPROVED", null).andExpect(status().isNotFound());
        }

        @Test
        void rejectRequiresComments() throws Exception {
            pendingWithManager(35L, "2");
            decide(35L, "mgr@lms.com", "REJECTED", "  ")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("Comments are mandatory on rejection"));
            verify(approvals, never()).save(any());
        }

        @Test
        void rejectWithComments() throws Exception {
            pendingWithManager(36L, "2");
            decide(36L, "mgr@lms.com", "REJECTED", "Release week")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
            verify(ledgers, never()).save(any());
        }

        @Test
        void invalidDecisionValueIs409() throws Exception {
            pendingWithManager(37L, "2");
            decide(37L, "mgr@lms.com", "MAYBE", null).andExpect(status().isConflict());
        }

        @Test
        void notPendingIs409() throws Exception {
            existing(38L, me, LeaveRequest.RequestStatus.APPROVED, "2");
            decide(38L, "mgr@lms.com", "APPROVED", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("Request is not currently awaiting a decision"));
        }

        @Test
        void noCurrentApproverIs409() throws Exception {
            existing(39L, me, LeaveRequest.RequestStatus.PENDING_L1, "2");
            decide(39L, "mgr@lms.com", "APPROVED", null).andExpect(status().isConflict());
        }

        @Test
        void outsiderCannotDecide() throws Exception {
            pendingWithManager(40L, "2");
            decide(40L, "hr@lms.com", "APPROVED", null).andExpect(status().isUnauthorized());
        }

        @Test
        void activeDelegateCanDecide() throws Exception {
            pendingWithManager(41L, "2");
            when(delegations.findActiveDelegation(eq(2L), eq(3L), any())).thenReturn(Optional.of(new ApprovalDelegation()));

            decide(41L, "hr@lms.com", "APPROVED", null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }

        @Test
        @DisplayName("comp-off claim larger than the remaining grant balance is refused at approval")
        void compOffBalanceCheckedAtApproval() throws Exception {
            LeaveRequest r = pendingWithManager(42L, "1");
            CompOffRequest grant = new CompOffRequest();
            grant.setId(14L);
            grant.setDaysCredited(BigDecimal.ONE);
            r.setCompOffRequest(grant);
            when(requests.sumDaysClaimedByCompOffRequestId(14L)).thenReturn(1.0);
            when(requests.sumDaysPendingByCompOffRequestId(14L)).thenReturn(1.0); // includes this request

            decide(42L, "mgr@lms.com", "APPROVED", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("INSUFFICIENT_COMP_OFF_BALANCE"));
        }

        @Test
        void compOffClaimWithinBalanceIsApproved() throws Exception {
            LeaveRequest r = pendingWithManager(43L, "1");
            CompOffRequest grant = new CompOffRequest();
            grant.setId(14L);
            grant.setDaysCredited(BigDecimal.ONE);
            r.setCompOffRequest(grant);
            when(requests.sumDaysClaimedByCompOffRequestId(14L)).thenReturn(0.0);
            when(requests.sumDaysPendingByCompOffRequestId(14L)).thenReturn(1.0);

            decide(43L, "mgr@lms.com", "APPROVED", null).andExpect(status().isOk());
        }
    }

    // =====================================================================
    @Nested
    class WithdrawApprovalsComments {

        @Test
        void withdrawAppendsReason() throws Exception {
            LeaveRequest r = existing(50L, me, LeaveRequest.RequestStatus.PENDING_L1, "2");

            mvc.perform(post("/api/v1/leave-requests/50/withdraw").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Plans changed"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
            assertThat(r.getReason()).isEqualTo("Family function [Withdrawal reason: Plans changed]");
        }

        @Test
        void withdrawRules() throws Exception {
            existing(51L, me, LeaveRequest.RequestStatus.APPROVED, "2");
            existing(52L, other, LeaveRequest.RequestStatus.PENDING_L1, "2");

            mvc.perform(post("/api/v1/leave-requests/51/withdraw").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/v1/leave-requests/52/withdraw").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void approvalHistory() throws Exception {
            LeaveRequest r = existing(53L, me, LeaveRequest.RequestStatus.APPROVED, "2");
            LeaveApproval a = new LeaveApproval();
            a.setId(1L);
            a.setRequest(r);
            a.setApprover(manager);
            a.setLevel((short) 1);
            a.setDecision(LeaveApproval.Decision.APPROVED);
            a.setDecidedAt(LocalDateTime.of(2030, 1, 3, 10, 0));
            a.setComments("ok");
            when(approvals.findByRequestId(53L)).thenReturn(List.of(a));

            mvc.perform(get("/api/v1/leave-requests/53/approvals").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].approverName").value("Manager 2"))
                    .andExpect(jsonPath("$[0].level").value(1))
                    .andExpect(jsonPath("$[0].decision").value("APPROVED"));
            mvc.perform(get("/api/v1/leave-requests/53/approvals").principal(auth("other@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void commentsAreStoredAndListed() throws Exception {
            long id = 9_000 + new Random().nextInt(100_000); // comment storage is static; keep ids unique
            existing(id, me, LeaveRequest.RequestStatus.PENDING_L1, "2");

            mvc.perform(post("/api/v1/leave-requests/" + id + "/comments").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "Back on Monday"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.authorName").value("Employee 1"));
            mvc.perform(post("/api/v1/leave-requests/" + id + "/comments").principal(auth("mgr@lms.com", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "Noted"))))
                    .andExpect(jsonPath("$.id").value(2));

            mvc.perform(get("/api/v1/leave-requests/" + id + "/comments").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].message").value("Noted"));
            mvc.perform(get("/api/v1/leave-requests/" + id + "/comments").principal(auth("other@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/leave-requests/" + id + "/comments").principal(auth("other@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("message", "x"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =====================================================================
    @Nested
    class HandoverAndAttachments {

        private HandoverTask task(Long id, LeaveRequest request) {
            HandoverTask t = new HandoverTask();
            t.setId(id);
            t.setLeaveRequest(request);
            t.setDescription("Deploy");
            return t;
        }

        @Test
        void applicantTicksOffTask() throws Exception {
            LeaveRequest r = existing(60L, me, LeaveRequest.RequestStatus.APPROVED, "2");
            HandoverTask t = task(1L, r);
            when(handoverTasks.findById(1L)).thenReturn(Optional.of(t));

            mvc.perform(patch("/api/v1/leave-requests/60/handover-tasks/1").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completed").value(true));
            assertThat(t.isCompleted()).isTrue();
        }

        @Test
        void handoverRules() throws Exception {
            LeaveRequest r = existing(61L, me, LeaveRequest.RequestStatus.APPROVED, "2");
            LeaveRequest elsewhere = leaveRequest(62L, me, casual, FRI, MON, "2", LeaveRequest.RequestStatus.APPROVED);
            when(handoverTasks.findById(2L)).thenReturn(Optional.of(task(2L, elsewhere)));

            mvc.perform(patch("/api/v1/leave-requests/61/handover-tasks/2").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/v1/leave-requests/61/handover-tasks/3").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/v1/leave-requests/61/handover-tasks/2").principal(auth("mgr@lms.com", "MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void attachmentVisibility() throws Exception {
            LeaveRequest r = existing(70L, me, LeaveRequest.RequestStatus.PENDING_L1, "2");
            when(attachments.listAttachments(Attachment.EntityType.LEAVE_REQUEST, 70L)).thenReturn(List.of(new AttachmentDto()));
            when(attachments.getAttachment(42L)).thenReturn(new AttachmentDto());

            mvc.perform(get("/api/v1/leave-requests/70/attachments").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/api/v1/leave-requests/70/attachments").principal(auth("other@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/leave-requests/70/attachments").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isUnauthorized()); // not the current approver yet
            r.setCurrentApprover(manager);
            mvc.perform(get("/api/v1/leave-requests/70/attachments").principal(auth("mgr@lms.com", "MANAGER")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/leave-requests/70/attachments/42").principal(auth("hr@lms.com", "HR_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        void uploadAndConfirmOnlyByOwner() throws Exception {
            existing(71L, me, LeaveRequest.RequestStatus.PENDING_L1, "2");
            when(attachments.initializeUpload(eq(Attachment.EntityType.LEAVE_REQUEST), eq(71L), any(), eq(1L)))
                    .thenReturn(new AttachmentInitUploadResponse(42L, "https://s3/put", "k"));
            when(attachments.confirmUpload(eq(42L), any())).thenReturn(new AttachmentDto());
            String upload = json(Map.of("fileName", "note.pdf", "contentType", "application/pdf", "sizeBytes", 100));

            mvc.perform(post("/api/v1/leave-requests/71/attachments/init-upload").principal(auth("emp@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(upload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.uploadUrl").value("https://s3/put"));
            mvc.perform(post("/api/v1/leave-requests/71/attachments/42/confirm").principal(auth("emp@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk());

            mvc.perform(post("/api/v1/leave-requests/71/attachments/init-upload").principal(auth("other@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(upload))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/leave-requests/71/attachments/42/confirm").principal(auth("other@lms.com", "EMPLOYEE")))
                    .andExpect(status().isUnauthorized());
        }
    }
}
