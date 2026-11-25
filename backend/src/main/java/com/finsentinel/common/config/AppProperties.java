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
