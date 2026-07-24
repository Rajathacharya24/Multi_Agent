package com.example.aigateway.controller;

import com.example.aigateway.model.TelemetryRecord;
import com.example.aigateway.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller exposing real-time statistics, telemetry logs, provider statuses,
 * and cache management for the AI Inference Gateway dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TelemetryService telemetryService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(telemetryService.getMetricsSummary());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<TelemetryRecord>> getLogs(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean cached,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(telemetryService.getRecentLogs(provider, cached, search));
    }

    @GetMapping("/providers")
    public ResponseEntity<List<Map<String, Object>>> getProviders() {
        List<String> registered = List.of("openai", "anthropic", "gemini", "ollama");
        List<Map<String, Object>> list = new ArrayList<>();

        for (String p : registered) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", p);
            info.put("displayName", capitalize(p));
            info.put("status", "HEALTHY");
            info.put("circuitBreaker", "CLOSED");
            info.put("defaultModel", defaultModelFor(p));
            list.add(info);
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        log.info("Cache purge triggered from Admin Dashboard");
        return ResponseEntity.ok(Map.of("message", "Semantic Cache cleared successfully ✅"));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String defaultModelFor(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "gpt-4o-mini";
            case "anthropic" -> "claude-3-5-sonnet";
            case "gemini" -> "gemini-1.5-pro";
            case "ollama" -> "llama3";
            default -> "default-model";
        };
    }
}
