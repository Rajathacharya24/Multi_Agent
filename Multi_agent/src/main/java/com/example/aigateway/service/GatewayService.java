package com.example.aigateway.service;

import com.example.aigateway.cache.SemanticCacheService;
import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import com.example.aigateway.provider.AiProvider;
import com.example.aigateway.router.ProviderRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Core gateway service: checks cache → routes → executes with fallback → records telemetry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    private final ProviderRouter router;
    private final SemanticCacheService cacheService;
    private final TelemetryService telemetryService;

    public ChatResponse process(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String requestedProvider = request.getProvider() != null ? request.getProvider() : "openai";
        String model = request.getModel() != null ? request.getModel() : defaultModel(requestedProvider);
        String cacheKey = buildCacheKey(requestedProvider, model, request.getMessages().toString());

        // 1. Check cache
        Optional<ChatResponse> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            long latency = System.currentTimeMillis() - startTime;
            ChatResponse hit = cached.get();
            hit.setCached(true);
            telemetryService.record(request, hit, latency, false);
            return hit;
        }

        // 2. Attempt providers in fallback order
        List<AiProvider> chain = router.getFallbackChain(requestedProvider);
        RuntimeException lastException = null;

        for (int i = 0; i < chain.size(); i++) {
            AiProvider provider = chain.get(i);
            boolean isFallback = i > 0;
            try {
                log.info("Trying provider: {} (fallback={})", provider.getName(), isFallback);
                ChatResponse response = provider.chat(request);

                // Ensure provider and model are set on response if missing
                if (response.getProvider() == null) response.setProvider(provider.getName());
                if (response.getModel() == null) response.setModel(model);

                long latency = System.currentTimeMillis() - startTime;

                // 3. Store in cache
                cacheService.put(cacheKey, response);

                // 4. Async telemetry
                telemetryService.record(request, response, latency, isFallback);

                return response;
            } catch (Exception e) {
                log.warn("Provider {} failed: {}. Trying next in fallback chain.", provider.getName(), e.getMessage());
                lastException = new RuntimeException(e.getMessage(), e);
            }
        }

        long totalLatency = System.currentTimeMillis() - startTime;
        String errorMsg = lastException != null ? lastException.getMessage() : "All AI providers unavailable";
        telemetryService.recordError(request, errorMsg, totalLatency);

        throw new RuntimeException("All providers failed. Last error: " + errorMsg, lastException);
    }

    private String defaultModel(String provider) {
        return "ollama".equals(provider) ? "llama3" : "gpt-4o-mini";
    }

    private String buildCacheKey(String provider, String model, String messages) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = provider + ":" + model + ":" + messages;
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(messages.hashCode());
        }
    }
}
