package com.finsentinel.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findTop100ByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.action = :action AND a.createdAt >= :since")
    long countByActionSince(String action, Instant since);

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.outcome = :outcome AND a.createdAt >= :since")
    long countByOutcomeSince(String outcome, Instant since);
}
