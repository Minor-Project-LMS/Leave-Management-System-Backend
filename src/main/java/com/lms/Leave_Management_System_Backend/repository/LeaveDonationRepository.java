package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.LeaveDonation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveDonationRepository extends JpaRepository<LeaveDonation, Long> {

    @EntityGraph(attributePaths = {"donor", "recipient", "category", "decidedBy"})
    Page<LeaveDonation> findByDonorId(Long donorId, Pageable pageable);

    @EntityGraph(attributePaths = {"donor", "recipient", "category", "decidedBy"})
    Page<LeaveDonation> findByRecipientId(Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"donor", "recipient", "category", "decidedBy"})
    @Query("SELECT d FROM LeaveDonation d WHERE (:status IS NULL OR d.status = :status)")
    Page<LeaveDonation> findWithStatus(@Param("status") LeaveDonation.Status status, Pageable pageable);
}
