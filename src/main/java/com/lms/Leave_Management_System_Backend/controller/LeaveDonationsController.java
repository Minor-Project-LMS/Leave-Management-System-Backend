package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.BusinessRuleException;
import com.lms.Leave_Management_System_Backend.exception.ConflictException;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveDonation;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveDonationRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import com.lms.Leave_Management_System_Backend.service.LeaveLedgerProvisioningService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Leave Donation / Pooling: an employee (donor) gives some of their unused
// balance, in a specific category, to a colleague (recipient) — e.g. for a
// hardship case. Requires HR approval before the transfer actually happens;
// the donor's available balance is re-checked at approval time (not just at
// request time), the same "recompute rather than trust a stale check"
// pattern already used for LOP at leave approval.
@RestController
@RequestMapping("/api/v1/leave-donations")
public class LeaveDonationsController {

    private final LeaveDonationRepository leaveDonationRepository;
    private final UserRepository userRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;
    private final LeaveLedgerRepository leaveLedgerRepository;
    private final LeaveLedgerProvisioningService leaveLedgerProvisioningService;

    public LeaveDonationsController(
            LeaveDonationRepository leaveDonationRepository,
            UserRepository userRepository,
            LeaveCategoryRepository leaveCategoryRepository,
            LeaveLedgerRepository leaveLedgerRepository,
            LeaveLedgerProvisioningService leaveLedgerProvisioningService) {
        this.leaveDonationRepository = leaveDonationRepository;
        this.userRepository = userRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.leaveLedgerProvisioningService = leaveLedgerProvisioningService;
    }

    // Leave donation is meant for a colleague who's genuinely running low
    // on that leave type — not a way to top up someone who still has their
    // normal quota intact. "Running low" means at or below this many days
    // remaining (covers the 0/1/2-days-left cases this is meant for);
    // above that, they still have their assigned quota and don't qualify.
    private static final BigDecimal RECIPIENT_MAX_BALANCE_TO_QUALIFY = BigDecimal.valueOf(2);

    // Only these leave types can be donated at all — Casual and Sick
    // Leave, specifically. Earned Leave (can carry monetary/encashment
    // value), Maternity Leave, Comp-Off, and any custom leave type are
    // deliberately excluded. Matched case-insensitively against the
    // category's display name, since that's what HR actually manages
    // (there's no separate "isDonatable" flag on LeaveCategory).
    private static final java.util.Set<String> DONATABLE_CATEGORY_NAMES =
            java.util.Set.of("casual leave", "sick leave");

    private void requireCategoryIsDonatable(LeaveCategory category) {
        String name = category.getName() == null ? "" : category.getName().trim().toLowerCase();
        if (!DONATABLE_CATEGORY_NAMES.contains(name)) {
            throw new BusinessRuleException(
                    category.getName() + " can't be donated — only Casual Leave and Sick Leave are eligible for donation.");
        }
    }

    // Provisions the recipient's ledger row first (rather than treating "no
    // row yet" as automatically eligible), then checks it's low enough.
    private void requireRecipientIsRunningLow(User recipient, LeaveCategory category, int fiscalYear) {
        leaveLedgerProvisioningService.getOrInitializeLedger(recipient, fiscalYear);
        Optional<LeaveLedger> recipientLedger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                recipient.getId(), category.getId(), fiscalYear);

        BigDecimal recipientBalance = recipientLedger.map(LeaveLedger::getClosingBalance).orElse(BigDecimal.ZERO);
        if (recipientBalance.compareTo(RECIPIENT_MAX_BALANCE_TO_QUALIFY) > 0) {
            throw new BusinessRuleException(
                    recipient.getFullName() + " still has " + recipientBalance + " day(s) available in "
                            + category.getName() + " — leave can only be donated to someone with "
                            + RECIPIENT_MAX_BALANCE_TO_QUALIFY + " day(s) or fewer remaining in that type.");
        }
    }

    private User currentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    @PostMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    @Transactional
    public ResponseEntity<LeaveDonationDto> createDonation(
            @Valid @RequestBody LeaveDonationRequest request,
            Authentication authentication) {

        User donor = currentUser(authentication);

        if (request.getRecipientId().equals(donor.getId())) {
            throw new BusinessRuleException("You can't donate leave to yourself.");
        }

        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getRecipientId()));

        LeaveCategory category = leaveCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("LeaveCategory", request.getCategoryId()));

        requireCategoryIsDonatable(category);

        int currentYear = Year.now().getValue();

        // Donation is reserved for someone who has genuinely run out —
        // not a way to top up a colleague who still has balance left.
        requireRecipientIsRunningLow(recipient, category, currentYear);

        leaveLedgerProvisioningService.getOrInitializeLedger(donor, currentYear);
        Optional<LeaveLedger> donorLedger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                donor.getId(), category.getId(), currentYear);

        BigDecimal available = donorLedger.map(LeaveLedger::getClosingBalance).orElse(BigDecimal.ZERO);
        if (available.compareTo(request.getDays()) < 0) {
            throw new BusinessRuleException(
                    "You only have " + available + " day(s) available in " + category.getName()
                            + " — can't donate " + request.getDays() + ".");
        }

        LeaveDonation donation = new LeaveDonation();
        donation.setDonor(donor);
        donation.setRecipient(recipient);
        donation.setCategory(category);
        donation.setDays(request.getDays());
        donation.setReason(request.getReason());
        donation.setStatus(LeaveDonation.Status.PENDING);

        leaveDonationRepository.save(donation);

        return ResponseEntity.status(201).body(toDto(donation));
    }

    // The donor's own sent requests, any status.
    @GetMapping("/mine")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveDonationDto>> listMyDonations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        User donor = currentUser(authentication);
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("requestedAt").descending());
        Page<LeaveDonation> result = leaveDonationRepository.findByDonorId(donor.getId(), pageable);

        return ResponseEntity.ok(toPaginated(result, page, limit));
    }

    // Donations received by the current user (approved or still pending),
    // so an employee can see what's been (or might be) given to them.
    @GetMapping("/received")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveDonationDto>> listReceivedDonations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {

        User recipient = currentUser(authentication);
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("requestedAt").descending());
        Page<LeaveDonation> result = leaveDonationRepository.findByRecipientId(recipient.getId(), pageable);

        return ResponseEntity.ok(toPaginated(result, page, limit));
    }

    // HR's review queue — every donation, optionally filtered by status
    // (typically "PENDING" for the queue view).
    @GetMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LeaveDonationDto>> listAllDonations(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        LeaveDonation.Status statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = LeaveDonation.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BusinessRuleException("Unknown status: " + status);
            }
        }

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("requestedAt").descending());
        Page<LeaveDonation> result = leaveDonationRepository.findWithStatus(statusFilter, pageable);

        return ResponseEntity.ok(toPaginated(result, page, limit));
    }

    @PatchMapping("/{donationId}/decision")
    @RequireRole({"HR_ADMIN"})
    @Transactional
    public ResponseEntity<LeaveDonationDto> decideDonation(
            @PathVariable Long donationId,
            @Valid @RequestBody LeaveDonationDecisionRequest request,
            Authentication authentication) {

        LeaveDonation donation = leaveDonationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveDonation", donationId));

        if (donation.getStatus() != LeaveDonation.Status.PENDING) {
            throw new ConflictException("This donation has already been " + donation.getStatus().name().toLowerCase() + ".");
        }

        String decision = request.getDecision() == null ? "" : request.getDecision().toUpperCase();
        if (!decision.equals("APPROVED") && !decision.equals("REJECTED")) {
            throw new BusinessRuleException("Decision must be APPROVED or REJECTED.");
        }
        if (decision.equals("REJECTED") && (request.getComments() == null || request.getComments().trim().isEmpty())) {
            throw new ConflictException("Comments are mandatory on rejection");
        }

        User hrUser = currentUser(authentication);

        if (decision.equals("APPROVED")) {
            int currentYear = Year.now().getValue();

            leaveLedgerProvisioningService.getOrInitializeLedger(donation.getDonor(), currentYear);
            leaveLedgerProvisioningService.getOrInitializeLedger(donation.getRecipient(), currentYear);

            // Defense in depth: re-check the category is still donatable
            // (in case the allowed list changes between request and
            // approval) and the recipient's eligibility too — they may
            // have accrued more leave, or received another approved
            // donation, since this request was first submitted.
            requireCategoryIsDonatable(donation.getCategory());
            requireRecipientIsRunningLow(donation.getRecipient(), donation.getCategory(), currentYear);

            LeaveLedger donorLedger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                            donation.getDonor().getId(), donation.getCategory().getId(), currentYear)
                    .orElseThrow(() -> new ResourceNotFoundException("LeaveLedger", donation.getDonor().getId()));

            // Re-check now, not just at request time — the donor's balance
            // may have shifted (e.g. they took leave in the meantime).
            if (donorLedger.getClosingBalance().compareTo(donation.getDays()) < 0) {
                throw new BusinessRuleException(
                        "Donor's balance has since dropped to " + donorLedger.getClosingBalance()
                                + " " + donation.getCategory().getName() + " day(s) — can no longer cover this donation of "
                                + donation.getDays() + ". Reject it or ask the donor to submit a smaller amount.");
            }

            LeaveLedger recipientLedger = leaveLedgerRepository.findByUserIdAndCategoryIdAndFiscalYear(
                            donation.getRecipient().getId(), donation.getCategory().getId(), currentYear)
                    .orElseThrow(() -> new ResourceNotFoundException("LeaveLedger", donation.getRecipient().getId()));

            donorLedger.setClosingBalance(donorLedger.getClosingBalance().subtract(donation.getDays()));
            donorLedger.setTransactionType("DONATED");
            donorLedger.setDescription("Donated " + donation.getDays() + " day(s) to " + donation.getRecipient().getFullName());
            donorLedger.setTransactionDate(java.time.LocalDate.now());
            leaveLedgerRepository.save(donorLedger);

            recipientLedger.setClosingBalance(recipientLedger.getClosingBalance().add(donation.getDays()));
            recipientLedger.setTransactionType("RECEIVED_DONATION");
            recipientLedger.setDescription("Received " + donation.getDays() + " day(s) from " + donation.getDonor().getFullName());
            recipientLedger.setTransactionDate(java.time.LocalDate.now());
            leaveLedgerRepository.save(recipientLedger);

            donation.setStatus(LeaveDonation.Status.APPROVED);
        } else {
            donation.setStatus(LeaveDonation.Status.REJECTED);
        }

        donation.setDecidedAt(LocalDateTime.now());
        donation.setDecidedBy(hrUser);
        donation.setDecisionComments(request.getComments());
        leaveDonationRepository.save(donation);

        return ResponseEntity.ok(toDto(donation));
    }

    private PaginatedResponse<LeaveDonationDto> toPaginated(Page<LeaveDonation> result, int page, int limit) {
        List<LeaveDonationDto> dtos = result.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                page, limit, result.getTotalElements(), result.getTotalPages());

        return new PaginatedResponse<>(true, dtos, pageResponse);
    }

    private LeaveDonationDto toDto(LeaveDonation d) {
        LeaveDonationDto dto = new LeaveDonationDto();
        dto.setId(d.getId());
        dto.setDonorId(d.getDonor().getId());
        dto.setDonorName(d.getDonor().getFullName());
        dto.setDonorEmployeeCode(d.getDonor().getEmployeeCode());
        dto.setRecipientId(d.getRecipient().getId());
        dto.setRecipientName(d.getRecipient().getFullName());
        dto.setRecipientEmployeeCode(d.getRecipient().getEmployeeCode());
        dto.setCategoryId(d.getCategory().getId());
        dto.setCategoryName(d.getCategory().getName());
        dto.setDays(d.getDays());
        dto.setReason(d.getReason());
        dto.setStatus(d.getStatus().name());
        dto.setRequestedAt(d.getRequestedAt());
        dto.setDecidedAt(d.getDecidedAt());
        dto.setDecidedByName(d.getDecidedBy() != null ? d.getDecidedBy().getFullName() : null);
        dto.setDecisionComments(d.getDecisionComments());
        return dto;
    }
}
