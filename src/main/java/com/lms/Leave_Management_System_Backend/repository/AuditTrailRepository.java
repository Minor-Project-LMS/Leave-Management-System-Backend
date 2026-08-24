package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {
    
    @EntityGraph(attributePaths = {"performedBy"})
    List<AuditTrail> findByEntityType(String entityType);
    
    @EntityGraph(attributePaths = {"performedBy"})
    List<AuditTrail> findByEntityTypeAndEntityId(String entityType, Long entityId);
    
    @EntityGraph(attributePaths = {"performedBy"})
    List<AuditTrail> findByPerformedById(Long performedById);
    
    @EntityGraph(attributePaths = {"performedBy"})
    List<AuditTrail> findByAction(AuditTrail.AuditAction action);
    
    @EntityGraph(attributePaths = {"performedBy"})
    List<AuditTrail> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(String entityType, Long entityId);
}