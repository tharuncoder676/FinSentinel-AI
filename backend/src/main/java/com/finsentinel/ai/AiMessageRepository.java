package com.finsentinel.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessageEntity, UUID> {
    List<AiMessageEntity> findByConversationIdAndUserIdOrderByCreatedAtAsc(UUID conversationId, UUID userId);
    long countByRole(String role);
}
