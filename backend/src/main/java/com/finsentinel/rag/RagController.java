package com.finsentinel.rag;

import com.finsentinel.common.security.UserSessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagKnowledgeService ragService;

    public RagController(RagKnowledgeService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/status")
    public RagDtos.RagStatusDto status() {
        return ragService.getStatus();
    }

    @GetMapping("/documents")
    public List<RagDtos.KnowledgeDocumentDto> listDocuments(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return ragService.listDocuments(principal.userId());
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public RagDtos.KnowledgeDocumentDto ingestDocument(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                       @Valid @RequestBody RagDtos.IngestDocumentRequest request) {
        return ragService.ingestDocument(principal, request);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@AuthenticationPrincipal UserSessionPrincipal principal,
                               @PathVariable UUID id) {
        ragService.deleteDocument(principal, id);
    }

    @PostMapping("/query")
    public RagDtos.QueryResponse query(@AuthenticationPrincipal UserSessionPrincipal principal,
                                       @Valid @RequestBody RagDtos.QueryRequest request) {
        return ragService.query(principal.userId(), request);
    }
}
