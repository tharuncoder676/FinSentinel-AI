package com.finsentinel.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReportEntity, UUID> {
    List<GeneratedReportEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<GeneratedReportEntity> findByIdAndUserId(UUID id, UUID userId);
}
