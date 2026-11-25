package com.finsentinel.admin;

import com.finsentinel.ai.AiMessageRepository;
import com.finsentinel.ai.AiOrchestrationService;
import com.finsentinel.auth.UserRepository;
import com.finsentinel.common.audit.AuditLogEntity;
import com.finsentinel.common.audit.AuditLogRepository;
import com.finsentinel.common.audit.AuditService;
import com.finsentinel.common.security.RateLimitFilter;
import com.finsentinel.common.security.UserSessionPrincipal;
import com.finsentinel.notification.NotificationRepository;
import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "app_configurations")
class AppConfigurationEntity {
    @Id
    @Column(name = "config_key", length = 80)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @Column(length = 300)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

interface AppConfigurationRepository extends JpaRepository<AppConfigurationEntity, String> {}

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final RateLimitFilter rateLimitFilter;
    private final AppConfigurationRepository configRepository;
    private final AuditService auditService;

    public AdminController(UserRepository userRepository,
                           AuditLogRepository auditLogRepository,
                           NotificationRepository notificationRepository,
                           AiMessageRepository aiMessageRepository,
                           AiOrchestrationService aiOrchestrationService,
                           RateLimitFilter rateLimitFilter,
                           AppConfigurationRepository configRepository,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.rateLimitFilter = rateLimitFilter;
        this.configRepository = configRepository;
        this.auditService = auditService;
    }

    public record AdminOverviewDto(
            long totalUsers,
            long activeUsers,
            long aiRequestsSinceBoot,
            long aiProviderErrorsSinceBoot,
            long totalAssistantMessagesStored,
            long rateLimitEventsSinceBoot,
            long failedLoginsLast24h,
            long notificationsDelivered,
            String privacyGuarantee,
            List<AuditLogRedactedDto> recentAuditLogs,
            List<AppConfigurationEntity> configurations
    ) {}

    public record AuditLogRedactedDto(
            String action,
            String actorRole,
            String resourceType,
            String outcome,
            String requestId,
            Instant createdAt
    ) {}

    @GetMapping("/overview")
    public AdminOverviewDto getOverview() {
        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        List<AuditLogRedactedDto> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .limit(30)
                .map(a -> new AuditLogRedactedDto(a.getAction(), a.getActorRole(), a.getResourceType(), a.getOutcome(), a.getRequestId(), a.getCreatedAt()))
                .toList();

        return new AdminOverviewDto(
                userRepository.count(),
                userRepository.countByStatus("ACTIVE"),
                aiOrchestrationService.getTotalRequests(),
                aiOrchestrationService.getProviderErrors(),
                aiMessageRepository.countByRole("ASSISTANT"),
                rateLimitFilter.getRateLimitedEvents(),
                auditLogRepository.countByActionSince("LOGIN_FAILED", dayAgo),
                notificationRepository.countByDeliveryStatus("DELIVERED"),
                "Aggregated operational metrics only. Administrators do not have access to private user transactions, balances, or AI conversation transcripts.",
                logs,
                configRepository.findAll()
        );
    }

    @PutMapping("/config/{key}")
    public AppConfigurationEntity updateConfig(@AuthenticationPrincipal UserSessionPrincipal principal,
                                               @PathVariable String key,
                                               @RequestBody Map<String, String> body) {
        AppConfigurationEntity cfg = configRepository.findById(key)
                .orElseGet(() -> {
                    AppConfigurationEntity c = new AppConfigurationEntity();
                    c.setConfigKey(key);
                    return c;
                });
        cfg.setConfigValue(body.getOrDefault("value", ""));
        if (body.containsKey("description")) cfg.setDescription(body.get("description"));
        cfg.setUpdatedAt(Instant.now());
        configRepository.save(cfg);
        auditService.record(principal.userId(), principal.role(), "ADMIN_CONFIG_UPDATED", "APP_CONFIGURATION", key, "SUCCESS", Map.of());
        return cfg;
    }
}
