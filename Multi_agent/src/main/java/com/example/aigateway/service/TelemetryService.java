package com.example.aigateway.service;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronously records token usage and cost estimates for telemetry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    /** Rough cost per 1M tokens for each provider (USD). */
    private static final java.util.Map<String, Double> COST_PER_1M_TOKENS = java.util.Map.of(
            "openai", 0.15,   // gpt-4o-mini input
            "ollama", 0.0     // self-hosted – free
    );

    @Async
    public void record(ChatRequest request, ChatResponse response) {
        if (response == null || response.getUsage() == null) return;

        int total = response.getUsage().getTotalTokens();
        double costPer1M = COST_PER_1M_TOKENS.getOrDefault(response.getProvider(), 0.0);
        double cost = (total / 1_000_000.0) * costPer1M;

        log.info("[TELEMETRY] provider={} model={} promptTokens={} completionTokens={} totalTokens={} estimatedCostUSD={}",
                response.getProvider(),
                response.getModel(),
                response.getUsage().getPromptTokens(),
                response.getUsage().getCompletionTokens(),
                total,
                String.format("%.6f", cost));
    }
}
