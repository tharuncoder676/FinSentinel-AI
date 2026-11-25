package com.finsentinel.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForecastSnapshotRepository extends JpaRepository<ForecastSnapshotEntity, UUID> {
    List<ForecastSnapshotEntity> findTop10ByUserIdOrderByGeneratedAtDesc(UUID userId);
}
