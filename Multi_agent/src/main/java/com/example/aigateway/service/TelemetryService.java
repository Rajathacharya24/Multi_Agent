package com.example.aigateway.service;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import com.example.aigateway.model.TelemetryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enhanced TelemetryService: Tracks token usage, costs, latencies, cache performance,
 * and maintains an in-memory ring buffer of proxy logs for the admin dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final int MAX_LOG_HISTORY = 500;

    /** Estimated cost per 1M tokens in USD */
    private static final Map<String, Double> COST_PER_1M_TOKENS = Map.of(
            "openai", 0.15,
            "anthropic", 3.00,
            "gemini", 0.075,
            "ollama", 0.0
    );

    private final ConcurrentLinkedDeque<TelemetryRecord> history = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalCacheHits = new AtomicLong(0);
    private final AtomicLong totalTokensAcc = new AtomicLong(0);
    private final AtomicLong totalLatencyAcc = new AtomicLong(0);

    public void record(ChatRequest request, ChatResponse response, long latencyMs, boolean fallbackUsed) {
        totalRequests.incrementAndGet();
        boolean isCached = response != null && response.isCached();
        if (isCached) {
            totalCacheHits.incrementAndGet();
        }

        int promptTokens = (response != null && response.getUsage() != null) ? response.getUsage().getPromptTokens() : 0;
        int completionTokens = (response != null && response.getUsage() != null) ? response.getUsage().getCompletionTokens() : 0;
        int total = (response != null && response.getUsage() != null) ? response.getUsage().getTotalTokens() : 0;

        totalTokensAcc.addAndGet(total);
        totalLatencyAcc.addAndGet(latencyMs);

        String providerName = (response != null && response.getProvider() != null) ? response.getProvider() : (request != null ? request.getProvider() : "unknown");
        double costPer1M = COST_PER_1M_TOKENS.getOrDefault(providerName.toLowerCase(), 0.15);
        double cost = (total / 1_000_000.0) * costPer1M;

        String promptPreview = extractPromptPreview(request);

        TelemetryRecord record = TelemetryRecord.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.now()))
                .provider(providerName)
                .model((response != null && response.getModel() != null) ? response.getModel() : (request != null ? request.getModel() : "unknown"))
                .promptPreview(promptPreview)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(total)
                .estimatedCostUsd(cost)
                .latencyMs(latencyMs)
                .cached(isCached)
                .fallbackUsed(fallbackUsed)
                .status(isCached ? "CACHE_HIT" : "SUCCESS")
                .build();

        history.addFirst(record);
        if (history.size() > MAX_LOG_HISTORY) {
            history.removeLast();
        }

        log.info("[TELEMETRY] id={} provider={} model={} totalTokens={} costUSD={} latencyMs={} cached={}",
                record.getId(), record.getProvider(), record.getModel(), total, String.format("%.6f", cost), latencyMs, isCached);
    }

    public void recordError(ChatRequest request, String errorMessage, long latencyMs) {
        totalRequests.incrementAndGet();
        totalLatencyAcc.addAndGet(latencyMs);

        String promptPreview = extractPromptPreview(request);

        TelemetryRecord record = TelemetryRecord.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.now()))
                .provider(request != null ? request.getProvider() : "unknown")
                .model(request != null ? request.getModel() : "unknown")
                .promptPreview(promptPreview)
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .estimatedCostUsd(0.0)
                .latencyMs(latencyMs)
                .cached(false)
                .fallbackUsed(false)
                .status("ERROR")
                .errorMessage(errorMessage)
                .build();

        history.addFirst(record);
        if (history.size() > MAX_LOG_HISTORY) {
            history.removeLast();
        }

        log.error("[TELEMETRY-ERROR] provider={} error={} latencyMs={}", request != null ? request.getProvider() : "unknown", errorMessage, latencyMs);
    }

    private String extractPromptPreview(ChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        String content = request.getMessages().get(request.getMessages().size() - 1).getContent();
        if (content == null) return "";
        return content.length() > 60 ? content.substring(0, 60) + "..." : content;
    }

    public List<TelemetryRecord> getRecentLogs(String provider, Boolean cached, String search) {
        return history.stream()
                .filter(r -> provider == null || provider.isBlank() || provider.equalsIgnoreCase("all") || r.getProvider().equalsIgnoreCase(provider))
                .filter(r -> cached == null || r.isCached() == cached)
                .filter(r -> search == null || search.isBlank() ||
                        (r.getPromptPreview() != null && r.getPromptPreview().toLowerCase().contains(search.toLowerCase())) ||
                        (r.getModel() != null && r.getModel().toLowerCase().contains(search.toLowerCase())) ||
                        (r.getProvider() != null && r.getProvider().toLowerCase().contains(search.toLowerCase())))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getMetricsSummary() {
        long reqs = totalRequests.get();
        long hits = totalCacheHits.get();
        long tokens = totalTokensAcc.get();
        long latencySum = totalLatencyAcc.get();

        double cacheHitRatio = reqs > 0 ? (hits * 100.0 / reqs) : 0.0;
        double avgLatency = reqs > 0 ? (latencySum * 1.0 / reqs) : 0.0;

        double savedCost = hits * 0.0003;
        double totalCost = history.stream().mapToDouble(TelemetryRecord::getEstimatedCostUsd).sum();

        Map<String, Long> byProvider = history.stream()
                .collect(Collectors.groupingBy(r -> r.getProvider().toLowerCase(), Collectors.counting()));

        Map<String, Object> map = new HashMap<>();
        map.put("totalRequests", reqs);
        map.put("totalCacheHits", hits);
        map.put("cacheHitRatioPercent", Math.round(cacheHitRatio * 10.0) / 10.0);
        map.put("totalTokens", tokens);
        map.put("totalCostUsd", Math.round(totalCost * 100000.0) / 100000.0);
        map.put("totalSavedCostUsd", Math.round(savedCost * 100000.0) / 100000.0);
        map.put("avgLatencyMs", Math.round(avgLatency));
        map.put("requestsByProvider", byProvider);

        return map;
    }
}
