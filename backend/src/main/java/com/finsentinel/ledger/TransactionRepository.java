package com.finsentinel.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID>, JpaSpecificationExecutor<TransactionEntity> {
    List<TransactionEntity> findByUserIdOrderByOccurredOnDescCreatedAtDesc(UUID userId);
    List<TransactionEntity> findByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(UUID userId, LocalDate from, LocalDate to);
    Page<TransactionEntity> findByUserId(UUID userId, Pageable pageable);
    Optional<TransactionEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndDedupHash(UUID userId, String dedupHash);
}
