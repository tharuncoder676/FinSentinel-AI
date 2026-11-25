package com.finsentinel.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversationEntity, UUID> {
    List<AiConversationEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<AiConversationEntity> findByIdAndUserId(UUID id, UUID userId);
}
