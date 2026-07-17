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
 * AiProvider implementation for Anthropic's Messages API.
 */
@Slf4j
@Component
public class AnthropicProvider implements AiProvider {

    @Value("${ai.providers.anthropic.api-key}")
    private String apiKey;

    @Value("${ai.providers.anthropic.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "anthropic";
    }

    @Override
    @CircuitBreaker(name = "anthropic", fallbackMethod = "chatFallback")
    public ChatResponse chat(ChatRequest request) {
        String model = (request.getModel() != null) ? request.getModel() : "claude-3-haiku-20240307";

        // Build the Anthropic-formatted messages list
        List<Map<String, String>> messages = request.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1024);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending request to Anthropic [model={}]", model);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/messages", HttpMethod.POST, entity, JsonNode.class);

        JsonNode json = response.getBody();
        String content = json.at("/content/0/text").asText();
        
        int promptTokens = 0;
        int completionTokens = 0;
        if (json.has("usage")) {
            promptTokens = json.at("/usage/input_tokens").asInt();
            completionTokens = json.at("/usage/output_tokens").asInt();
        }

        return ChatResponse.builder()
                .provider("anthropic")
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
        log.warn("Anthropic circuit breaker triggered: {}", t.getMessage());
        throw new RuntimeException("Anthropic provider unavailable – circuit breaker open.", t);
    }
}
