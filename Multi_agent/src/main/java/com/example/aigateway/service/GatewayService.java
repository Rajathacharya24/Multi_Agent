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
        String model = request.getModel() != null ? request.getModel() : defaultModel(request.getProvider());
        String cacheKey = buildCacheKey(request.getProvider(), model, request.getMessages().toString());

        // 1. Check cache
        Optional<ChatResponse> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            ChatResponse hit = cached.get();
            hit.setCached(true);
            return hit;
        }

        // 2. Attempt providers in fallback order
        List<AiProvider> chain = router.getFallbackChain(request.getProvider());
        RuntimeException lastException = null;

        for (AiProvider provider : chain) {
            try {
                log.info("Trying provider: {}", provider.getName());
                ChatResponse response = provider.chat(request);

                // 3. Store in cache
                cacheService.put(cacheKey, response);

                // 4. Async telemetry
                telemetryService.record(request, response);

                return response;
            } catch (Exception e) {
                log.warn("Provider {} failed: {}. Trying next in fallback chain.", provider.getName(), e.getMessage());
                lastException = new RuntimeException(e.getMessage(), e);
            }
        }

        throw new RuntimeException("All providers failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown"), lastException);
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
