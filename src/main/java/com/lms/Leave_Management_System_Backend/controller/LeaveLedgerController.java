package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/leave-ledger")
public class LeaveLedgerController {

    private final LeaveLedgerRepository leaveLedgerRepository;
    private final UserRepository userRepository;

    public LeaveLedgerController(
            LeaveLedgerRepository leaveLedgerRepository,
            UserRepository userRepository) {
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<LeaveLedgerSummaryDto>> getLeaveLedgerSummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        
        // If userId not provided, use current user
        if (userId == null) {
            String email = authentication.getName();
            User currentUser = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", email));
            userId = currentUser.getId();
        }

        // Default to current year if not provided
        if (year == null) {
            year = LocalDate.now().getYear();
        }

        List<LeaveLedger> ledgerEntries = leaveLedgerRepository.findByUserIdAndFiscalYear(userId, year, PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        List<LeaveLedgerSummaryDto> summaryList = ledgerEntries.stream()
                .map(this::toLeaveLedgerSummaryDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaryList);
    }

    @GetMapping("/transactions")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<LedgerTransactionDto>> getTransactionHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(value = "year", required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            Authentication authentication) {

        // If userId not provided, use current user
        if (userId == null) {
            String email = authentication.getName();
            User currentUser = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User", email));
            userId = currentUser.getId();
        }

        // Default to current fiscal year if not provided
        if (fiscalYear == null) {
            fiscalYear = LocalDate.now().getYear();
        }

        Pageable pageable = PageRequest.of(page - 1, limit,
            sort != null ? Sort.by(sort) : Sort.by("transactionDate").descending());

        Page<LeaveLedger> transactions = leaveLedgerRepository.findWithFilters(
            userId, fiscalYear, categoryId, pageable);

        List<LedgerTransactionDto> dtoList = transactions.getContent().stream()
                .map(this::toLedgerTransactionDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
            page,
            limit,
            transactions.getTotalElements(),
            transactions.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, dtoList, pageResponse));
    }

    @GetMapping("/export")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<String> exportLedgerCsv(
            @RequestParam(required = false) Integer fiscalYear,
            Authentication authentication) {
        
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (fiscalYear == null) {
            fiscalYear = LocalDate.now().getYear();
        }

        List<LeaveLedger> ledgerEntries = leaveLedgerRepository.findByUserIdAndFiscalYear(
            currentUser.getId(), fiscalYear, PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        StringBuilder csv = new StringBuilder();
        csv.append("Category,Fiscal Year,Opening Balance,Accrued,Used,Encashed,Carried Forward,Closing Balance\n");

        for (LeaveLedger entry : ledgerEntries) {
            csv.append(entry.getCategory().getName()).append(",");
            csv.append(entry.getFiscalYear()).append(",");
            csv.append(entry.getOpeningBalance()).append(",");
            csv.append(entry.getAccrued()).append(",");
            csv.append(entry.getUsed()).append(",");
            csv.append(entry.getEncashed()).append(",");
            csv.append(entry.getCarriedForward()).append(",");
            csv.append(entry.getClosingBalance()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=ledger_" + fiscalYear + ".csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    private LeaveLedgerEntryDto toLeaveLedgerEntryDto(LeaveLedger ledger) {
        LeaveLedgerEntryDto dto = new LeaveLedgerEntryDto();
        dto.setLedgerId(ledger.getId().intValue());
        dto.setCategoryId(ledger.getCategory().getId());
        dto.setCategoryName(ledger.getCategory().getName());
        dto.setFiscalYear(ledger.getFiscalYear());
        dto.setOpeningBalance(ledger.getOpeningBalance().doubleValue());
        dto.setAccrued(ledger.getAccrued().doubleValue());
        dto.setUsed(ledger.getUsed().doubleValue());
        dto.setEncashed(ledger.getEncashed().doubleValue());
        dto.setCarriedForward(ledger.getCarriedForward().doubleValue());
        dto.setClosingBalance(ledger.getClosingBalance().doubleValue());
        return dto;
    }

    private LeaveLedgerSummaryDto toLeaveLedgerSummaryDto(LeaveLedger ledger) {
        LeaveLedgerSummaryDto dto = new LeaveLedgerSummaryDto();
        dto.setCategoryId(ledger.getCategory().getId());
        dto.setCategoryName(ledger.getCategory().getName());
        dto.setFiscalYear(ledger.getFiscalYear());
        dto.setOpeningBalance(ledger.getOpeningBalance().doubleValue());
        dto.setAccrued(ledger.getAccrued().doubleValue());
        dto.setUsed(ledger.getUsed().doubleValue());
        dto.setEncashed(ledger.getEncashed().doubleValue());
        dto.setCarriedForward(ledger.getCarriedForward().doubleValue());
        dto.setClosingBalance(ledger.getClosingBalance().doubleValue());
        dto.setAvailableBalance(ledger.getClosingBalance().doubleValue());
        return dto;
    }

    private LedgerTransactionDto toLedgerTransactionDto(LeaveLedger ledger) {
        LedgerTransactionDto dto = new LedgerTransactionDto();
        dto.setDate(ledger.getTransactionDate() != null ? ledger.getTransactionDate() : LocalDate.now());
        dto.setCategoryName(ledger.getCategory().getName());
        dto.setDescription(ledger.getDescription() != null ? ledger.getDescription() : "Balance update");
        
        // Determine credit/debit based on transaction type
        if ("CREDIT".equals(ledger.getTransactionType())) {
            dto.setCredit(ledger.getAccrued().doubleValue());
            dto.setDebit(0.0);
        } else if ("DEBIT".equals(ledger.getTransactionType())) {
            dto.setCredit(0.0);
            dto.setDebit(ledger.getUsed().doubleValue());
        } else {
            dto.setCredit(0.0);
            dto.setDebit(0.0);
        }
        
        dto.setRunningBalance(ledger.getClosingBalance().doubleValue());
        dto.setReferenceType(ledger.getReferenceType());
        dto.setReferenceId(ledger.getReferenceId() != null ? ledger.getReferenceId().intValue() : null);
        return dto;
    }
}