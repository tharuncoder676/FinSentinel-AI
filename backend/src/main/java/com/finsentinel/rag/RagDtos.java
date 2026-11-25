package com.finsentinel.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RagDtos {

    public record KnowledgeDocumentDto(
            UUID id,
            String slug,
            String title,
            String category,
            String scope,
            String sourceType,
            int chunkCount,
            String preview,
            Instant updatedAt
    ) {}

    public record IngestDocumentRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 64) String category,
            @NotBlank @Size(max = 30000) String content,
            String scope
    ) {}

    public record QueryRequest(
            @NotBlank @Size(max = 1000) String query,
            Integer topK
    ) {}

    public record RetrievedChunkDto(
            UUID documentId,
            String documentTitle,
            String category,
            String scope,
            int chunkIndex,
            String heading,
            String content,
            double score,
            String retrievalMode,
            String citation
    ) {}

    public record QueryResponse(
            String query,
            String retrievalMode,
            List<RetrievedChunkDto> results
    ) {}

    public record RagStatusDto(
            boolean enabled,
            long totalDocuments,
            long totalChunks,
            boolean pgvectorAvailable,
            String embeddingMode
    ) {}
}
