package com.finsentinel.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsentinel.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiProviderFactoryTest {

    private AppProperties propsWithAi(String provider, String model, String mistralModel,
                                      String nvidiaKey1, String nvidiaKey2, String nvidiaKey3,
                                      String nvidiaKeysCsv, String mistralKey) {
        return new AppProperties(
                "http://localhost:5173",
                "http://localhost:5173",
                new AppProperties.Security("FS_SESSION", false, "Lax", 480, "XSRF-TOKEN"),
                new AppProperties.Mail(false, "no-reply@finsentinel.local"),
                new AppProperties.Ai(
                        provider,
                        model,
                        mistralModel,
                        "nvidia/nemotron-3.5-embed-1b",
                        true,
                        nvidiaKey1,
                        nvidiaKey2,
                        nvidiaKey3,
                        nvidiaKeysCsv,
                        mistralKey,
                        "",
                        "",
                        "",
                        "",
                        15000,
                        1024
                ),
                new AppProperties.Demo(false)
        );
    }

    @Test
    void buildsOrderedThreeKeyNvidiaChainFollowedStrictlyByMistralFallback() {
        AiProviderFactory factory = new AiProviderFactory(
                propsWithAi("nvidia", "nvidia/nemotron-3.5-bolt-e4b", "mistral-large-latest",
                        "nvapi-key-1", "nvapi-key-2", "nvapi-key-3", "", "mistral-fallback-key"),
                new ObjectMapper()
        );

        List<AiProviderAdapter> chain = factory.resolveFailoverChain();
        assertEquals(4, chain.size());
        assertEquals("nvidia#key-1", chain.get(0).providerName());
        assertEquals("nvidia/nemotron-3.5-bolt-e4b", chain.get(0).configuredModel());
        assertEquals("nvidia#key-2", chain.get(1).providerName());
        assertEquals("nvidia#key-3", chain.get(2).providerName());
        assertEquals("mistral", chain.get(3).providerName());
        assertEquals("mistral-large-latest", chain.get(3).configuredModel());
        assertTrue(factory.describeFailoverSummary().contains("3 keys"));
        assertTrue(factory.describeFailoverSummary().contains("mistral fallback"));
    }

    @Test
    void supportsCommaSeparatedNvidiaKeysAndDegradesCleanlyWhenUnconfigured() {
        AiProviderFactory csvFactory = new AiProviderFactory(
                propsWithAi("", "", "", "", "", "", "nvapi-a, nvapi-b, nvapi-a, nvapi-c", "mistral-key"),
                new ObjectMapper()
        );
        List<AiProviderAdapter> chain = csvFactory.resolveFailoverChain();
        assertEquals(4, chain.size());
        assertEquals(List.of("nvidia#key-1", "nvidia#key-2", "nvidia#key-3", "mistral"),
                chain.stream().map(AiProviderAdapter::providerName).toList());

        AiProviderFactory emptyFactory = new AiProviderFactory(
                propsWithAi("", "", "", "", "", "", "", ""),
                new ObjectMapper()
        );
        AiProviderAdapter none = emptyFactory.resolveActiveAdapter();
        assertEquals("none", none.providerName());
        assertFalse(none.isConfigured());
        assertTrue(emptyFactory.embedTexts(List.of("hello"), "query").isEmpty());
    }
}
