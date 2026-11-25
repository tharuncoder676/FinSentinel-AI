package com.finsentinel.ai;

import java.util.function.Consumer;

public interface AiProviderAdapter {
    String providerName();
    boolean isConfigured();
    String configuredModel();
    CompletionResult complete(String systemPrompt, String userPrompt, Consumer<String> onDelta, java.util.concurrent.atomic.AtomicBoolean cancelled) throws Exception;

    record CompletionResult(
            String text,
            int tokensUsed,
            String modelUsed
    ) {}
}
