package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveDonation;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveDonationRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.lms.Leave_Management_System_Backend.support.MvcTestSupport.*;
import static com.lms.Leave_Management_System_Backend.support.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeaveDonationsControllerTest {

    private static final int YEAR = Year.now().getValue();

    private LeaveDonationRepository donations;
    private UserRepository users;
    private LeaveCategoryRepository categories;
    private LeaveLedgerRepository ledgers;
    private LeaveLedgerProvisioningService provisioning;
    private MockMvc mvc;

    private User donor;
    private User recipient;
    private User hrUser;
    private LeaveCategory casual;
    private LeaveLedger donorLedger;
    private LeaveLedger recipientLedger;

    @BeforeEach
    void setUp() {
        donations = mock(LeaveDonationRepository.class);
        users = mock(UserRepository.class);
        categories = mock(LeaveCategoryRepository.class);
        ledgers = mock(LeaveLedgerRepository.class);
        provisioning = mock(LeaveLedgerProvisioningService.class);
        mvc = mockMvc(new LeaveDonationsController(donations, users, categories, ledgers, provisioning));

        donor = employee(1L, "donor@lms.com");
        recipient = employee(2L, "recipient@lms.com");
        hrUser = hr(3L, "hr@lms.com");
        casual = category(1, "Casual Leave");
        donorLedger = ledger(donor, casual, YEAR, "6");
        recipientLedger = ledger(recipient, casual, YEAR, "1");

        when(users.findByEmailIgnoreCase("donor@lms.com")).thenReturn(Optional.of(donor));
        when(users.findByEmailIgnoreCase("hr@lms.com")).thenReturn(Optional.of(hrUser));
        when(users.findById(2L)).thenReturn(Optional.of(recipient));
        when(categories.findById(1)).thenReturn(Optional.of(casual));
        when(ledgers.findByUserIdAndCategoryIdAndFiscalYear(1L, 1, YEAR)).thenReturn(Optional.of(donorLedger));
        when(ledgers.findByUserIdAndCategoryIdAndFiscalYear(2L, 1, YEAR)).thenReturn(Optional.of(recipientLedger));
    }

    private String request(String days) {
        return json(Map.of("recipientId", 2, "categoryId", 1, "days", new BigDecimal(days), "reason", "Medical emergency"));
    }

    @Nested
    class Create {

        @Test
        void success() throws Exception {
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(request("2")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.donorName").value("Employee 1"))
                    .andExpect(jsonPath("$.recipientName").value("Employee 2"))
                    .andExpect(jsonPath("$.days").value(2));
            verify(donations).save(any(LeaveDonation.class));
            verify(provisioning).getOrInitializeLedger(recipient, YEAR);
            verify(provisioning).getOrInitializeLedger(donor, YEAR);
        }

        @Test
        void cannotDonateToSelf() throws Exception {
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("recipientId", 1, "categoryId", 1, "days", 1))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value("You can't donate leave to yourself."));
        }

        @Test
        @DisplayName("only Casual and Sick leave can be donated")
        void categoryMustBeDonatable() throws Exception {
            when(categories.findById(1)).thenReturn(Optional.of(category(1, "Earned Leave")));
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(request("1")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value(containsString("only Casual Leave and Sick Leave")));
        }

        @Test
        @DisplayName("recipient with more than 2 days left does not qualify")
        void recipientMustBeRunningLow() throws Exception {
            recipientLedger.setClosingBalance(new BigDecimal("2.5"));
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(request("1")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value(containsString("still has 2.5 day(s)")));
        }

        @Test
        void donorMustHaveEnoughBalance() throws Exception {
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(request("7")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value(containsString("You only have 6 day(s)")));
            verify(donations, never()).save(any());
        }

        @Test
        void minimumHalfDayIsValidated() throws Exception {
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON).content(request("0.25")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        void unknownRecipientIs404() throws Exception {
            mvc.perform(post("/api/v1/leave-donations").principal(auth("donor@lms.com", "EMPLOYEE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("recipientId", 99, "categoryId", 1, "days", 1))))
                    .andExpect(status().isNotFound());
        }
    }

    private LeaveDonation pendingDonation(String days) {
        LeaveDonation d = new LeaveDonation();
        d.setId(10L);
        d.setDonor(donor);
        d.setRecipient(recipient);
        d.setCategory(casual);
        d.setDays(new BigDecimal(days));
        d.setStatus(LeaveDonation.Status.PENDING);
        return d;
    }

    @Nested
    class Lists {

        @Test
        void mineReceivedAndAll() throws Exception {
            when(donations.findByDonorId(eq(1L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pendingDonation("1"))));
            when(users.findByEmailIgnoreCase("recipient@lms.com")).thenReturn(Optional.of(recipient));
            when(donations.findByRecipientId(eq(2L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pendingDonation("1"))));
            when(donations.findWithStatus(eq(LeaveDonation.Status.PENDING), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pendingDonation("1"))));

            mvc.perform(get("/api/v1/leave-donations/mine").principal(auth("donor@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(10));
            mvc.perform(get("/api/v1/leave-donations/received").principal(auth("recipient@lms.com", "EMPLOYEE")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
            mvc.perform(get("/api/v1/leave-donations").param("status", "pending"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("PENDING"));
        }

        @Test
        void unknownStatusFilterIs422() throws Exception {
            mvc.perform(get("/api/v1/leave-donations").param("status", "LOST"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void noStatusFilterPassesNull() throws Exception {
            when(donations.findWithStatus(isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
            mvc.perform(get("/api/v1/leave-donations")).andExpect(status().isOk());
            verify(donations).findWithStatus(isNull(), any(Pageable.class));
        }
    }

    @Nested
    class Decide {

        private String decision(String d, String comments) {
            return comments == null ? json(Map.of("decision", d)) : json(Map.of("decision", d, "comments", comments));
        }

        @Test
        @DisplayName("approval moves the days from donor to recipient")
        void approveTransfersBalance() throws Exception {
            LeaveDonation d = pendingDonation("1.5");
            when(donations.findById(10L)).thenReturn(Optional.of(d));

            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("approved", null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.decidedByName").value("HR 3"));

            assertThat(donorLedger.getClosingBalance()).isEqualByComparingTo("4.5");
            assertThat(donorLedger.getTransactionType()).isEqualTo("DONATED");
            assertThat(recipientLedger.getClosingBalance()).isEqualByComparingTo("2.5");
            assertThat(recipientLedger.getTransactionType()).isEqualTo("RECEIVED_DONATION");
            verify(ledgers).save(donorLedger);
            verify(ledgers).save(recipientLedger);
        }

        @Test
        @DisplayName("approval re-checks the donor balance at decision time")
        void approveFailsWhenDonorBalanceDropped() throws Exception {
            donorLedger.setClosingBalance(new BigDecimal("0.5"));
            when(donations.findById(10L)).thenReturn(Optional.of(pendingDonation("1")));

            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("APPROVED", null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.message").value(containsString("Donor's balance has since dropped")));
            verify(ledgers, never()).save(any());
        }

        @Test
        void approveFailsWhenRecipientNoLongerQualifies() throws Exception {
            recipientLedger.setClosingBalance(new BigDecimal("5"));
            when(donations.findById(10L)).thenReturn(Optional.of(pendingDonation("1")));

            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("APPROVED", null)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void rejectRequiresComments() throws Exception {
            when(donations.findById(10L)).thenReturn(Optional.of(pendingDonation("1")));

            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("REJECTED", " ")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("Comments are mandatory on rejection"));
        }

        @Test
        void rejectWithComments() throws Exception {
            LeaveDonation d = pendingDonation("1");
            when(donations.findById(10L)).thenReturn(Optional.of(d));

            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("REJECTED", "Policy limit")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.decisionComments").value("Policy limit"));
            verify(ledgers, never()).save(any());
        }

        @Test
        void alreadyDecidedIs409() throws Exception {
            LeaveDonation d = pendingDonation("1");
            d.setStatus(LeaveDonation.Status.APPROVED);
            when(donations.findById(10L)).thenReturn(Optional.of(d));

            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("APPROVED", null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("This donation has already been approved."));
        }

        @Test
        void invalidDecisionIs422() throws Exception {
            when(donations.findById(10L)).thenReturn(Optional.of(pendingDonation("1")));
            mvc.perform(patch("/api/v1/leave-donations/10/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("MAYBE", null)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void unknownDonationIs404() throws Exception {
            mvc.perform(patch("/api/v1/leave-donations/99/decision").principal(auth("hr@lms.com", "HR_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(decision("APPROVED", null)))
                    .andExpect(status().isNotFound());
        }
    }
}
