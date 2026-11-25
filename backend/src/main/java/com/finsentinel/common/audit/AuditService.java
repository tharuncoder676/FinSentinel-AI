package com.finsentinel.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.api.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID userId, String actorRole, String action, String resourceType, String resourceId, String outcome, Map<String, Object> metadata) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setActorRole(actorRole);
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setRequestId(MDC.get(RequestIdFilter.MDC_KEY));
        entity.setOutcome(outcome);
        entity.setCreatedAt(Instant.now());
        if (metadata != null && !metadata.isEmpty()) {
            try {
                entity.setMetadataJson(objectMapper.writeValueAsString(metadata));
            } catch (Exception ignored) {
                entity.setMetadataJson("{}");
            }
        }
        auditLogRepository.save(entity);
    }
}
