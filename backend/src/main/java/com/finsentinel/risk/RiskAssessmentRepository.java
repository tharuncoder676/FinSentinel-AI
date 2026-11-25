package com.finsentinel.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessmentEntity, UUID> {
    List<RiskAssessmentEntity> findTop24ByUserIdOrderByCalculatedAtDesc(UUID userId);
}
