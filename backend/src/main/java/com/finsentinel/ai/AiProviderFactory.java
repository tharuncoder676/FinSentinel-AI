package com.finsentinel.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class AiProviderFactory {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiProviderFactory(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        int timeoutMs = Math.max(3000, appProperties.ai().requestTimeoutMs());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(10000, timeoutMs)))
                .build();
    }

    public AiProviderAdapter resolveActiveAdapter() {
        String provider = appProperties.ai().provider() != null ? appProperties.ai().provider().trim().toLowerCase() : "";
        return switch (provider) {
            case "openai" -> new OpenAiCompatibleAdapter("openai", "https://api.openai.com", appProperties.ai().openaiApiKey(),
                    configuredModelOrEmpty(), appProperties, objectMapper, httpClient);
            case "gemini" -> new GeminiAdapter(appProperties, objectMapper, httpClient);
            case "anthropic" -> new AnthropicAdapter(appProperties, objectMapper, httpClient);
            case "local" -> new OpenAiCompatibleAdapter("local", appProperties.ai().localBaseUrl(), "local-optional",
                    configuredModelOrEmpty(), appProperties, objectMapper, httpClient);
            default -> {
                if (hasText(appProperties.ai().geminiApiKey())) yield new GeminiAdapter(appProperties, objectMapper, httpClient);
                if (hasText(appProperties.ai().openaiApiKey())) yield new OpenAiCompatibleAdapter("openai", "https://api.openai.com",
                        appProperties.ai().openaiApiKey(), configuredModelOrEmpty(), appProperties, objectMapper, httpClient);
                if (hasText(appProperties.ai().anthropicApiKey())) yield new AnthropicAdapter(appProperties, objectMapper, httpClient);
                if (hasText(appProperties.ai().localBaseUrl())) yield new OpenAiCompatibleAdapter("local", appProperties.ai().localBaseUrl(),
                        "local-optional", configuredModelOrEmpty(), appProperties, objectMapper, httpClient);
                yield new UnconfiguredAdapter();
            }
        };
    }

    private String configuredModelOrEmpty() {
        return appProperties.ai().model() != null ? appProperties.ai().model().trim() : "";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static class UnconfiguredAdapter implements AiProviderAdapter {
        @Override public String providerName() { return "none"; }
        @Override public boolean isConfigured() { return false; }
        @Override public String configuredModel() { return ""; }
        @Override
        public CompletionResult complete(String systemPrompt, String userPrompt, Consumer<String> onDelta, AtomicBoolean cancelled) {
            throw new IllegalStateException("No AI provider API key is configured.");
        }
    }

    private static class OpenAiCompatibleAdapter implements AiProviderAdapter {
        private final String name;
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final AppProperties props;
        private final ObjectMapper mapper;
        private final HttpClient client;

        OpenAiCompatibleAdapter(String name, String baseUrl, String apiKey, String model, AppProperties props, ObjectMapper mapper, HttpClient client) {
            this.name = name;
            this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
            this.apiKey = apiKey;
            this.model = model;
            this.props = props;
            this.mapper = mapper;
            this.client = client;
        }

        @Override public String providerName() { return name; }
        @Override public boolean isConfigured() {
            return hasText(baseUrl) && hasText(model) && ("local".equals(name) || hasText(apiKey));
        }
        @Override public String configuredModel() { return model; }

        @Override
        public CompletionResult complete(String systemPrompt, String userPrompt, Consumer<String> onDelta, AtomicBoolean cancelled) throws Exception {
            if (!isConfigured()) throw new IllegalStateException("AI_MODEL or API key missing for provider " + name);
            Map<String, Object> body = Map.of(
                    "model", model,
                    "stream", true,
                    "max_tokens", Math.min(2048, Math.max(128, props.ai().maxOutputTokens())),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofMillis(Math.max(3000, props.ai().requestTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (hasText(apiKey) && !"local".equals(name)) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<InputStream> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Provider " + name + " returned HTTP " + response.statusCode());
            }
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled != null && cancelled.get()) break;
                    if (!line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) break;
                    JsonNode node = mapper.readTree(payload);
                    String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        full.append(delta);
                        if (onDelta != null) onDelta.accept(delta);
                    }
                }
            }
            return new CompletionResult(full.toString(), Math.max(1, full.length() / 4), model);
        }
    }

    private static class GeminiAdapter implements AiProviderAdapter {
        private final AppProperties props;
        private final ObjectMapper mapper;
        private final HttpClient client;

        GeminiAdapter(AppProperties props, ObjectMapper mapper, HttpClient client) {
            this.props = props;
            this.mapper = mapper;
            this.client = client;
        }

        @Override public String providerName() { return "gemini"; }
        @Override public boolean isConfigured() { return hasText(props.ai().geminiApiKey()) && hasText(configuredModel()); }
        @Override public String configuredModel() { return props.ai().model() != null ? props.ai().model().trim() : ""; }

        @Override
        public CompletionResult complete(String systemPrompt, String userPrompt, Consumer<String> onDelta, AtomicBoolean cancelled) throws Exception {
            if (!isConfigured()) throw new IllegalStateException("GEMINI_API_KEY and AI_MODEL are required for Gemini.");
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + configuredModel() + ":streamGenerateContent?alt=sse";
            Map<String, Object> body = Map.of(
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                    "generationConfig", Map.of("maxOutputTokens", Math.min(2048, Math.max(128, props.ai().maxOutputTokens())))
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(3000, props.ai().requestTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", props.ai().geminiApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<InputStream> response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Gemini API returned HTTP " + response.statusCode());
            }
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled != null && cancelled.get()) break;
                    if (!line.startsWith("data:")) continue;
                    JsonNode node = mapper.readTree(line.substring(5).trim());
                    String delta = node.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
                    if (!delta.isEmpty()) {
                        full.append(delta);
                        if (onDelta != null) onDelta.accept(delta);
                    }
                }
            }
            return new CompletionResult(full.toString(), Math.max(1, full.length() / 4), configuredModel());
        }
    }

    private static class AnthropicAdapter implements AiProviderAdapter {
        private final AppProperties props;
        private final ObjectMapper mapper;
        private final HttpClient client;

        AnthropicAdapter(AppProperties props, ObjectMapper mapper, HttpClient client) {
            this.props = props;
            this.mapper = mapper;
            this.client = client;
        }

        @Override public String providerName() { return "anthropic"; }
        @Override public boolean isConfigured() { return hasText(props.ai().anthropicApiKey()) && hasText(configuredModel()); }
        @Override public String configuredModel() { return props.ai().model() != null ? props.ai().model().trim() : ""; }

        @Override
        public CompletionResult complete(String systemPrompt, String userPrompt, Consumer<String> onDelta, AtomicBoolean cancelled) throws Exception {
            if (!isConfigured()) throw new IllegalStateException("ANTHROPIC_API_KEY and AI_MODEL are required for Anthropic.");
            Map<String, Object> body = Map.of(
                    "model", configuredModel(),
                    "max_tokens", Math.min(2048, Math.max(128, props.ai().maxOutputTokens())),
                    "stream", true,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", userPrompt))
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofMillis(Math.max(3000, props.ai().requestTimeoutMs())))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", props.ai().anthropicApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<InputStream> response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Anthropic API returned HTTP " + response.statusCode());
            }
            StringBuilder full = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled != null && cancelled.get()) break;
                    if (!line.startsWith("data:")) continue;
                    JsonNode node = mapper.readTree(line.substring(5).trim());
                    if ("content_block_delta".equals(node.path("type").asText())) {
                        String delta = node.path("delta").path("text").asText("");
                        if (!delta.isEmpty()) {
                            full.append(delta);
                            if (onDelta != null) onDelta.accept(delta);
                        }
                    }
                }
            }
            return new CompletionResult(full.toString(), Math.max(1, full.length() / 4), configuredModel());
        }
    }
}
