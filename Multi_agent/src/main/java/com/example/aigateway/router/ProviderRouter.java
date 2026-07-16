package com.example.aigateway.router;

import com.example.aigateway.provider.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes incoming requests to the correct AiProvider
 * and supports automatic fallback ordering.
 */
@Slf4j
@Service
public class ProviderRouter {

    /** Map of provider name -> provider bean, auto-discovered via Spring. */
    private final Map<String, AiProvider> providers;

    /** Default fallback chain: if one fails, try the next. */
    private static final List<String> FALLBACK_CHAIN = List.of("openai", "ollama");

    public ProviderRouter(List<AiProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(AiProvider::getName, Function.identity()));
        log.info("Registered AI providers: {}", providers.keySet());
    }

    /**
     * Returns the provider for the given name.
     * Falls back to the next provider in the FALLBACK_CHAIN if the requested one is unavailable.
     *
     * @param preferredProvider name of the preferred provider
     * @return the selected AiProvider
     */
    public AiProvider select(String preferredProvider) {
        if (providers.containsKey(preferredProvider)) {
            return providers.get(preferredProvider);
        }
        log.warn("Provider '{}' not found. Using fallback chain.", preferredProvider);
        return FALLBACK_CHAIN.stream()
                .filter(providers::containsKey)
                .map(providers::get)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No AI providers available"));
    }

    /**
     * Returns the full fallback chain starting from the requested provider.
     */
    public List<AiProvider> getFallbackChain(String startProvider) {
        List<String> chain = new java.util.ArrayList<>(FALLBACK_CHAIN);
        // Ensure the requested provider is first
        chain.remove(startProvider);
        chain.add(0, startProvider);
        return chain.stream()
                .filter(providers::containsKey)
                .map(providers::get)
                .toList();
    }
}
