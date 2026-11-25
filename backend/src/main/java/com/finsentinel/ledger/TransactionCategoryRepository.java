package com.finsentinel.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategoryEntity, UUID> {
    @Query("SELECT c FROM TransactionCategoryEntity c WHERE c.system = true OR c.userId = :userId ORDER BY c.system DESC, c.name ASC")
    List<TransactionCategoryEntity> findAvailableForUser(UUID userId);
}
