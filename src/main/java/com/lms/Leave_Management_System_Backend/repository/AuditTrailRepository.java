package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {
    
    List<AuditTrail> findByEntityType(String entityType);
    
    List<AuditTrail> findByEntityTypeAndEntityId(String entityType, Long entityId);
    
    List<AuditTrail> findByPerformedById(Long performedById);
    
    List<AuditTrail> findByAction(AuditTrail.AuditAction action);
    
    List<AuditTrail> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(String entityType, Long entityId);
}