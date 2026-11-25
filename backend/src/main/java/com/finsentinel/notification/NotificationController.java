package com.finsentinel.notification;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record PrefsRequest(
            boolean inAppEnabled,
            boolean emailEnabled,
            int reminderDaysBefore,
            boolean shortfallAlertsEnabled
    ) {}

    @GetMapping
    public List<NotificationEntity> list(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return notificationService.listNotifications(principal.userId());
    }

    @PostMapping("/evaluate")
    public Map<String, Integer> evaluateNow(@AuthenticationPrincipal UserSessionPrincipal principal) {
        int created = notificationService.evaluateRemindersForUser(principal.userId());
        return Map.of("created", created);
    }

    @PatchMapping("/{id}/read")
    public NotificationEntity markRead(@AuthenticationPrincipal UserSessionPrincipal principal,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) Map<String, Boolean> body) {
        boolean read = body == null || body.getOrDefault("read", true);
        return notificationService.markRead(principal, id, read);
    }

    @PostMapping("/read-all")
    public Map<String, String> markAllRead(@AuthenticationPrincipal UserSessionPrincipal principal) {
        notificationService.markAllRead(principal.userId());
        return Map.of("status", "OK");
    }

    @GetMapping("/preferences")
    public NotificationPreferencesEntity getPreferences(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return notificationService.getPreferences(principal.userId());
    }

    @PutMapping("/preferences")
    public NotificationPreferencesEntity updatePreferences(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                         @RequestBody PrefsRequest request) {
        return notificationService.updatePreferences(
                principal.userId(),
                request.inAppEnabled(),
                request.emailEnabled(),
                request.reminderDaysBefore(),
                request.shortfallAlertsEnabled()
        );
    }
}
