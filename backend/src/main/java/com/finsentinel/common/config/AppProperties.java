package com.finsentinel.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendOrigin,
        String baseUrl,
        Security security,
        Mail mail,
        Ai ai,
        Demo demo
) {
    public record Security(
            String sessionCookieName,
            boolean sessionCookieSecure,
            String sessionCookieSameSite,
            int sessionTtlMinutes,
            String csrfCookieName
    ) {}

    public record Mail(
            boolean enabled,
            String from
    ) {}

    public record Ai(
            String provider,
            String model,
            String mistralModel,
            String embeddingModel,
            boolean ragEnabled,
            String nvidiaApiKey,
            String nvidiaApiKey2,
            String nvidiaApiKey3,
            String nvidiaApiKeys,
            String mistralApiKey,
            String openaiApiKey,
            String geminiApiKey,
            String anthropicApiKey,
            String localBaseUrl,
            int requestTimeoutMs,
            int maxOutputTokens
    ) {}

    public record Demo(
            boolean seedEnabled
    ) {}
}
