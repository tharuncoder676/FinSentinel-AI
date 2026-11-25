package com.finsentinel.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, UUID> {

    Optional<KnowledgeDocumentEntity> findBySlugAndScope(String slug, String scope);

    @Query("SELECT d FROM KnowledgeDocumentEntity d WHERE d.scope = 'SYSTEM' OR d.ownerUserId = :userId ORDER BY d.scope ASC, d.updatedAt DESC")
    List<KnowledgeDocumentEntity> findAccessibleDocuments(@Param("userId") UUID userId);

    Optional<KnowledgeDocumentEntity> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
