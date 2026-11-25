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
        List<AiProviderAdapter> chain = resolveFailoverChain();
        return chain.isEmpty() ? new UnconfiguredAdapter() : chain.get(0);
    }

    /**
     * Resolves an ordered failover chain of configured providers.
     * When NVIDIA keys are configured (1..N via NVIDIA_API_KEY, NVIDIA_API_KEY_2/3, or NVIDIA_API_KEYS),
     * all NVIDIA keys are attempted sequentially first; Mistral is placed strictly after all NVIDIA keys
     * so Mistral is invoked only when every configured NVIDIA key fails.
     */
    public List<AiProviderAdapter> resolveFailoverChain() {
        String provider = appProperties.ai().provider() != null ? appProperties.ai().provider().trim().toLowerCase() : "";
        List<AiProviderAdapter> chain = new java.util.ArrayList<>();

        List<String> nvidiaKeys = resolveNvidiaKeys();
        String nvidiaModel = configuredModelOrDefault("nvidia/nemotron-3.5-bolt-e4b");
        String mistralModel = hasText(appProperties.ai().mistralModel())
                ? appProperties.ai().mistralModel().trim()
                : ( "mistral".equals(provider) ? configuredModelOrDefault("mistral-large-latest") : "mistral-large-latest" );

        if ("mistral".equals(provider)) {
            addMistralAdapter(chain, mistralModel);
            addNvidiaAdapters(chain, nvidiaKeys, nvidiaModel);
        } else if ("openai".equals(provider)) {
            if (hasText(appProperties.ai().openaiApiKey())) {
                chain.add(new OpenAiCompatibleAdapter("openai", "https://api.openai.com",
                        appProperties.ai().openaiApiKey(), configuredModelOrDefault("gpt-4o-mini"),
                        appProperties, objectMapper, httpClient));
            }
            addNvidiaAdapters(chain, nvidiaKeys, nvidiaModel);
            addMistralAdapter(chain, mistralModel);
        } else if ("gemini".equals(provider)) {
            if (hasText(appProperties.ai().geminiApiKey())) {
                chain.add(new GeminiAdapter(appProperties, objectMapper, httpClient));
            }
            addNvidiaAdapters(chain, nvidiaKeys, nvidiaModel);
            addMistralAdapter(chain, mistralModel);
        } else if ("anthropic".equals(provider)) {
            if (hasText(appProperties.ai().anthropicApiKey())) {
                chain.add(new AnthropicAdapter(appProperties, objectMapper, httpClient));
            }
            addNvidiaAdapters(chain, nvidiaKeys, nvidiaModel);
            addMistralAdapter(chain, mistralModel);
        } else if ("local".equals(provider)) {
            if (hasText(appProperties.ai().localBaseUrl())) {
                chain.add(new OpenAiCompatibleAdapter("local", appProperties.ai().localBaseUrl(), "local-optional",
                        configuredModelOrEmpty(), appProperties, objectMapper, httpClient));
            }
        } else {
            // Default / "nvidia" / "auto": NVIDIA key chain (#1 -> #2 -> #3), then Mistral fallback ONLY if all NVIDIA keys fail
            addNvidiaAdapters(chain, nvidiaKeys, nvidiaModel);
            addMistralAdapter(chain, mistralModel);
            if (hasText(appProperties.ai().geminiApiKey())) chain.add(new GeminiAdapter(appProperties, objectMapper, httpClient));
            if (hasText(appProperties.ai().openaiApiKey())) chain.add(new OpenAiCompatibleAdapter("openai", "https://api.openai.com",
                    appProperties.ai().openaiApiKey(), configuredModelOrDefault("gpt-4o-mini"), appProperties, objectMapper, httpClient));
            if (hasText(appProperties.ai().anthropicApiKey())) chain.add(new AnthropicAdapter(appProperties, objectMapper, httpClient));
            if (hasText(appProperties.ai().localBaseUrl())) chain.add(new OpenAiCompatibleAdapter("local", appProperties.ai().localBaseUrl(),
                    "local-optional", configuredModelOrEmpty(), appProperties, objectMapper, httpClient));
        }
        return chain;
    }

    public List<String> resolveNvidiaKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        addSplitKeys(keys, appProperties.ai().nvidiaApiKey());
        addSplitKeys(keys, appProperties.ai().nvidiaApiKey2());
        addSplitKeys(keys, appProperties.ai().nvidiaApiKey3());
        addSplitKeys(keys, appProperties.ai().nvidiaApiKeys());
        return List.copyOf(keys);
    }

    public String describeFailoverSummary() {
        List<AiProviderAdapter> chain = resolveFailoverChain();
        if (chain.isEmpty() || !chain.get(0).isConfigured()) {
            return "No live external AI provider key/model configured on server. Deterministic backend + RAG explanations remain available.";
        }
        int nvidiaCount = resolveNvidiaKeys().size();
        boolean hasMistral = hasText(appProperties.ai().mistralApiKey());
        AiProviderAdapter primary = chain.get(0);
        if (primary.providerName().startsWith("nvidia") && nvidiaCount > 0) {
            return "Live AI armed: nvidia (" + nvidiaCount + " key" + (nvidiaCount > 1 ? "s" : "")
                    + ", model: " + primary.configuredModel() + ")"
                    + (hasMistral ? " -> mistral fallback when all NVIDIA keys fail" : "");
        }
        return "Live AI provider '" + primary.providerName() + "' configured (model: " + primary.configuredModel() + ").";
    }

    private void addNvidiaAdapters(List<AiProviderAdapter> chain, List<String> keys, String nvidiaModel) {
        for (int i = 0; i < keys.size(); i++) {
            String tag = (keys.size() == 1) ? "nvidia" : "nvidia#key-" + (i + 1);
            chain.add(new OpenAiCompatibleAdapter(tag, "https://integrate.api.nvidia.com",
                    keys.get(i), nvidiaModel, appProperties, objectMapper, httpClient));
        }
    }

    private void addMistralAdapter(List<AiProviderAdapter> chain, String mistralModel) {
        if (hasText(appProperties.ai().mistralApiKey())) {
            chain.add(new OpenAiCompatibleAdapter("mistral", "https://api.mistral.ai",
                    appProperties.ai().mistralApiKey(), mistralModel, appProperties, objectMapper, httpClient));
        }
    }

    private static void addSplitKeys(java.util.Set<String> target, String raw) {
        if (!hasText(raw)) return;
        for (String part : raw.split("[,;\\r\\n]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    /**
     * Generates dense embeddings using NVIDIA NIM (rotating through all configured NVIDIA keys on failure)
     * or OpenAI fallback when available. Returns empty gracefully offline so hybrid FTS + local vectors run seamlessly.
     */
    public java.util.Optional<List<double[]>> embedTexts(List<String> texts, String inputType) {
        if (texts == null || texts.isEmpty()) {
            return java.util.Optional.empty();
        }
        List<String> nvidiaKeys = resolveNvidiaKeys();
        if (!nvidiaKeys.isEmpty()) {
            String embedModel = hasText(appProperties.ai().embeddingModel())
                    ? appProperties.ai().embeddingModel().trim()
                    : "nvidia/nemotron-3.5-embed-1b";
            for (String key : nvidiaKeys) {
                java.util.Optional<List<double[]>> res = callEmbeddingEndpoint(
                        "https://integrate.api.nvidia.com/v1/embeddings", key, embedModel, texts, inputType, true);
                if (res.isPresent()) {
                    return res;
                }
            }
        }
        if (hasText(appProperties.ai().openaiApiKey())) {
            String embedModel = hasText(appProperties.ai().embeddingModel()) && !appProperties.ai().embeddingModel().startsWith("nvidia/")
                    ? appProperties.ai().embeddingModel().trim()
                    : "text-embedding-3-small";
            return callEmbeddingEndpoint(
                    "https://api.openai.com/v1/embeddings", appProperties.ai().openaiApiKey(), embedModel, texts, inputType, false);
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<List<double[]>> callEmbeddingEndpoint(String url, String apiKey, String embedModel,
                                                                     List<String> texts, String inputType, boolean isNvidia) {
        try {
            java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", embedModel);
            body.put("input", texts);
            if (isNvidia) {
                body.put("input_type", (inputType != null && !inputType.isBlank()) ? inputType : "passage");
                body.put("encoding_format", "float");
                body.put("truncate", "END");
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.min(15000, Math.max(4000, appProperties.ai().requestTimeoutMs()))))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return java.util.Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataArr = root.path("data");
            if (!dataArr.isArray() || dataArr.size() != texts.size()) {
                return java.util.Optional.empty();
            }
            List<double[]> vectors = new java.util.ArrayList<>(dataArr.size());
            for (JsonNode item : dataArr) {
                JsonNode emb = item.path("embedding");
                if (!emb.isArray() || emb.isEmpty()) {
                    return java.util.Optional.empty();
                }
                double[] vec = new double[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = emb.get(i).asDouble(0.0);
                }
                vectors.add(vec);
            }
            return java.util.Optional.of(vectors);
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private String configuredModelOrEmpty() {
        return appProperties.ai().model() != null ? appProperties.ai().model().trim() : "";
    }

    private String configuredModelOrDefault(String fallbackModel) {
        String configured = configuredModelOrEmpty();
        return configured.isEmpty() ? fallbackModel : configured;
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
                    if (delta.isEmpty()) {
                        delta = node.path("choices").path(0).path("delta").path("reasoning_content").asText("");
                    }
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
