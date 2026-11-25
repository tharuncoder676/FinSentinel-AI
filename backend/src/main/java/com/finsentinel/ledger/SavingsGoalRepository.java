package com.finsentinel.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoalEntity, UUID> {
    List<SavingsGoalEntity> findByUserIdOrderByPriorityAscCreatedAtAsc(UUID userId);
    Optional<SavingsGoalEntity> findByIdAndUserId(UUID id, UUID userId);
}
