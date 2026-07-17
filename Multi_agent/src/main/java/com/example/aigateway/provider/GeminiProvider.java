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
 * AiProvider implementation for Google Gemini's generateContent endpoint.
 */
@Slf4j
@Component
public class GeminiProvider implements AiProvider {

    @Value("${ai.providers.gemini.api-key}")
    private String apiKey;

    @Value("${ai.providers.gemini.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "gemini";
    }

    @Override
    @CircuitBreaker(name = "gemini", fallbackMethod = "chatFallback")
    public ChatResponse chat(ChatRequest request) {
        String model = (request.getModel() != null) ? request.getModel() : "gemini-1.5-flash";

        // Build the Gemini-formatted contents list
        List<Map<String, Object>> contents = request.getMessages().stream()
                .map(m -> {
                    String role = "assistant".equals(m.getRole()) ? "model" : m.getRole();
                    return Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", m.getContent()))
                    );
                })
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending request to Gemini [model={}]", model);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/models/" + model + ":generateContent", HttpMethod.POST, entity, JsonNode.class);

        JsonNode json = response.getBody();
        String content = json.at("/candidates/0/content/parts/0/text").asText();
        
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        if (json.has("usageMetadata")) {
            promptTokens = json.at("/usageMetadata/promptTokenCount").asInt();
            completionTokens = json.at("/usageMetadata/candidatesTokenCount").asInt();
            totalTokens = json.at("/usageMetadata/totalTokenCount").asInt();
        }

        return ChatResponse.builder()
                .provider("gemini")
                .model(model)
                .content(content)
                .usage(ChatResponse.Usage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(totalTokens > 0 ? totalTokens : promptTokens + completionTokens)
                        .build())
                .cached(false)
                .build();
    }

    /** Called by Resilience4j when the circuit is open. */
    public ChatResponse chatFallback(ChatRequest request, Throwable t) {
        log.warn("Gemini circuit breaker triggered: {}", t.getMessage());
        throw new RuntimeException("Gemini provider unavailable – circuit breaker open.", t);
    }
}
