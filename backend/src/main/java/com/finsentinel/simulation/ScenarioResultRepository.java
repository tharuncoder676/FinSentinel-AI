package com.finsentinel.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioResultRepository extends JpaRepository<ScenarioResultEntity, UUID> {
    List<ScenarioResultEntity> findBySimulationIdAndUserIdOrderByGeneratedAtDesc(UUID simulationId, UUID userId);
}
