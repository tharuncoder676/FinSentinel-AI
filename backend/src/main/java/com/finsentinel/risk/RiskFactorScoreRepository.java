package com.finsentinel.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskFactorScoreRepository extends JpaRepository<RiskFactorScoreEntity, UUID> {
    List<RiskFactorScoreEntity> findByAssessmentIdOrderByWeightDesc(UUID assessmentId);
}
