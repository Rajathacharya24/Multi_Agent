package com.example.aigateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {

    private String provider;
    private String model;
    private String content;
    private Usage usage;
    private boolean cached;

    @Data
    @Builder
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
