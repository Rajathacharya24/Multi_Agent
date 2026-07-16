package com.example.aigateway.cache;

import com.example.aigateway.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Simple exact-match caching layer backed by Redis.
 * Key is a deterministic hash of (provider + model + messages).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "aigw:cache:";

    private final RedisTemplate<String, ChatResponse> redisTemplate;

    public Optional<ChatResponse> get(String cacheKey) {
        ChatResponse cached = redisTemplate.opsForValue().get(KEY_PREFIX + cacheKey);
        if (cached != null) {
            log.info("Cache HIT for key={}", cacheKey);
            return Optional.of(cached);
        }
        log.debug("Cache MISS for key={}", cacheKey);
        return Optional.empty();
    }

    public void put(String cacheKey, ChatResponse response) {
        redisTemplate.opsForValue().set(KEY_PREFIX + cacheKey, response, TTL);
        log.info("Cached response for key={}", cacheKey);
    }

    /**
     * Builds a deterministic cache key from the request parameters.
     */
    public String buildKey(String provider, String model, String messagesHash) {
        return String.format("%s:%s:%s", provider, model, messagesHash);
    }
}
