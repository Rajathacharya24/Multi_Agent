package com.example.aigateway.provider;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
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
 * AiProvider implementation for local Ollama server.
 */
@Slf4j
@Component
public class OllamaProvider implements AiProvider {

    @Value("${ai.providers.ollama.url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public OllamaProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    @CircuitBreaker(name = "ollama", fallbackMethod = "chatFallback")
    public ChatResponse chat(ChatRequest request) {
        String model = (request.getModel() != null) ? request.getModel() : "llama3";

        List<Map<String, String>> messages = request.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending request to Ollama [model={}]", model);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/chat", HttpMethod.POST, entity, JsonNode.class);

        JsonNode json = response.getBody();
        String content = json.at("/message/content").asText();
        int promptTokens = json.at("/prompt_eval_count").asInt(0);
        int completionTokens = json.at("/eval_count").asInt(0);

        return ChatResponse.builder()
                .provider("ollama")
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

    public ChatResponse chatFallback(ChatRequest request, Throwable t) {
        log.warn("Ollama circuit breaker triggered: {}", t.getMessage());
        throw new RuntimeException("Ollama provider unavailable – circuit breaker open.", t);
    }
}
