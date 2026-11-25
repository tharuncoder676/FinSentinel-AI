package com.finsentinel.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {
    List<ExpenseEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<ExpenseEntity> findByIdAndUserId(UUID id, UUID userId);
}
