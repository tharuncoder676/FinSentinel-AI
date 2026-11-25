package com.finsentinel.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialObligationRepository extends JpaRepository<FinancialObligationEntity, UUID> {
    List<FinancialObligationEntity> findByUserIdOrderByDueDateAsc(UUID userId);
    Optional<FinancialObligationEntity> findByIdAndUserId(UUID id, UUID userId);
}
