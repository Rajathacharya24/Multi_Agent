package com.example.aigateway.controller;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import com.example.aigateway.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Normalized gateway controller.
 * Accepts OpenAI-compatible chat requests and routes them through the gateway.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayService gatewayService;

    /**
     * POST /v1/chat/completions
     * Unified chat completion endpoint compatible with OpenAI SDK format.
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Incoming request → provider={} model={} messages={}",
                request.getProvider(), request.getModel(), request.getMessages().size());
        ChatResponse response = gatewayService.process(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/health
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Inference Gateway is running ✅");
    }
}
