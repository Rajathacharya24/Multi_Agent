package com.example.aigateway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {

    /** Target provider: "openai", "ollama". Defaults to "openai". */
    private String provider = "openai";

    /** The model to use. Provider-specific (e.g. "gpt-4o", "llama3"). */
    private String model;

    @NotNull
    @NotEmpty
    private List<Message> messages;

    private boolean stream = false;

    @Data
    public static class Message {
        @NotNull
        private String role;   // "system", "user", "assistant"
        @NotNull
        private String content;
    }
}
