package com.example.aigateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryRecord {
    private String id;
    private String timestamp;
    private String provider;
    private String model;
    private String promptPreview;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private double estimatedCostUsd;
    private long latencyMs;
    private boolean cached;
    private boolean fallbackUsed;
    private String status; // "SUCCESS", "CACHE_HIT", "ERROR"
    private String errorMessage;
}
