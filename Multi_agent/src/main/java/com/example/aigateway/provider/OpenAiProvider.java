package com.example.aigateway.provider;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AiProvider implementation for OpenAI's chat completions endpoint.
 */
@Slf4j
@Component
public class OpenAiProvider implements AiProvider {

    @Value("${ai.providers.openai.api-key}")
    private String apiKey;

    @Value("${ai.providers.openai.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    @CircuitBreaker(name = "openai", fallbackMethod = "chatFallback")
    public ChatResponse chat(ChatRequest request) {
        String model = (request.getModel() != null) ? request.getModel() : "gpt-4o-mini";

        // Build the OpenAI-formatted messages list
        List<Map<String, String>> messages = request.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending request to OpenAI [model={}]", model);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/chat/completions", HttpMethod.POST, entity, JsonNode.class);

        JsonNode json = response.getBody();
        String content = json.at("/choices/0/message/content").asText();
        int promptTokens = json.at("/usage/prompt_tokens").asInt();
        int completionTokens = json.at("/usage/completion_tokens").asInt();

        return ChatResponse.builder()
                .provider("openai")
                .model(model)
                .content(content)
                .usage(ChatResponse.Usage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .build())
                .cached(false)
                .build();
    }

    /** Called by Resilience4j when the circuit is open. */
    public ChatResponse chatFallback(ChatRequest request, Throwable t) {
        log.warn("OpenAI circuit breaker triggered: {}", t.getMessage());
        throw new RuntimeException("OpenAI provider unavailable – circuit breaker open.", t);
    }
}
