package com.finsentinel.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    List<AccountEntity> findByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);
    List<AccountEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<AccountEntity> findByIdAndUserId(UUID id, UUID userId);
}
