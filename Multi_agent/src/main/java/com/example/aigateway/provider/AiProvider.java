package com.example.aigateway.provider;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;

/**
 * Strategy interface for all AI providers.
 * Each implementation handles communication with a specific AI backend.
 */
public interface AiProvider {

    /** Unique name of this provider (e.g. "openai", "ollama"). */
    String getName();

    /**
     * Send a chat completion request to the underlying AI provider.
     *
     * @param request unified gateway request
     * @return unified gateway response
     */
    ChatResponse chat(ChatRequest request);
}
