package com.lms.Leave_Management_System_Backend.service;

import com.lms.Leave_Management_System_Backend.model.LeaveCategory;
import com.lms.Leave_Management_System_Backend.model.LeaveLedger;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.LeaveCategoryRepository;
import com.lms.Leave_Management_System_Backend.repository.LeaveLedgerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The ledger for a brand-new fiscal year (or for a category added after the
 * employee joined) previously just didn't exist until some other process
 * created it, so the Leave Ledger page came back empty as soon as the
 * calendar rolled over to a year nobody had provisioned yet.
 *
 * This service fetches the existing entries for a user/year and, for any
 * active leave category that doesn't have one, creates it on the fly using
 * the category's default annual quota (plus whatever was left over from the
 * previous fiscal year, carried forward). That keeps "view the ledger" a
 * read operation from the caller's point of view while guaranteeing every
 * active category always has a row to show.
 */
@Service
public class LeaveLedgerProvisioningService {

    private final LeaveLedgerRepository leaveLedgerRepository;
    private final LeaveCategoryRepository leaveCategoryRepository;

    public LeaveLedgerProvisioningService(LeaveLedgerRepository leaveLedgerRepository,
                                          LeaveCategoryRepository leaveCategoryRepository) {
        this.leaveLedgerRepository = leaveLedgerRepository;
        this.leaveCategoryRepository = leaveCategoryRepository;
    }

    public List<LeaveLedger> getOrInitializeLedger(User user, int fiscalYear) {
        List<LeaveLedger> existing = leaveLedgerRepository.findByUserIdAndFiscalYear(user.getId(), fiscalYear);

        List<LeaveCategory> activeCategories = leaveCategoryRepository.findAll().stream()
                .filter(c -> c.getStatus() == null || "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .collect(Collectors.toList());

        List<Integer> existingCategoryIds = existing.stream()
                .map(l -> l.getCategory().getId())
                .collect(Collectors.toList());

        boolean createdAny = false;
        for (LeaveCategory category : activeCategories) {
            if (existingCategoryIds.contains(category.getId())) {
                continue;
            }

            BigDecimal opening = category.getDefaultAnnualQuota() != null
                    ? BigDecimal.valueOf(category.getDefaultAnnualQuota())
                    : BigDecimal.ZERO;

            BigDecimal carriedForward = BigDecimal.ZERO;
            Optional<LeaveLedger> previousYear = leaveLedgerRepository
                    .findByUserIdAndCategoryIdAndFiscalYear(user.getId(), category.getId(), fiscalYear - 1);
            if (previousYear.isPresent()) {
                carriedForward = previousYear.get().getClosingBalance();
            }

            LeaveLedger entry = new LeaveLedger();
            entry.setUser(user);
            entry.setCategory(category);
            entry.setFiscalYear(fiscalYear);
            entry.setOpeningBalance(opening);
            entry.setAccrued(BigDecimal.ZERO);
            entry.setUsed(BigDecimal.ZERO);
            entry.setEncashed(BigDecimal.ZERO);
            entry.setCarriedForward(carriedForward);
            entry.setClosingBalance(opening.add(carriedForward));
            entry.setTransactionDate(LocalDate.of(fiscalYear, 1, 1));
            entry.setTransactionType("SYSTEM");
            entry.setDescription("Opening Balance");

            leaveLedgerRepository.save(entry);
            createdAny = true;
        }

        return createdAny
                ? leaveLedgerRepository.findByUserIdAndFiscalYear(user.getId(), fiscalYear)
                : existing;
    }
}
