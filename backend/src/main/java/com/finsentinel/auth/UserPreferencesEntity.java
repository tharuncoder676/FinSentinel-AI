package com.finsentinel.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferencesEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String theme = "system";

    @Column(nullable = false, length = 8)
    private String language = "en";

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Column(nullable = false, length = 16)
    private String locale = "en-IN";

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "ai_external_consent", nullable = false)
    private boolean aiExternalConsent = true;

    @Column(name = "reduced_motion", nullable = false)
    private boolean reducedMotion = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isAiExternalConsent() { return aiExternalConsent; }
    public void setAiExternalConsent(boolean aiExternalConsent) { this.aiExternalConsent = aiExternalConsent; }
    public boolean isReducedMotion() { return reducedMotion; }
    public void setReducedMotion(boolean reducedMotion) { this.reducedMotion = reducedMotion; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
