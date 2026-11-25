package com.finsentinel.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioSimulationRepository extends JpaRepository<ScenarioSimulationEntity, UUID> {
    List<ScenarioSimulationEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<ScenarioSimulationEntity> findByIdAndUserId(UUID id, UUID userId);
}
