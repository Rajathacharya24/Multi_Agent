package com.example.aigateway.cache;

import com.example.aigateway.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resilient Semantic Cache layer backed by Redis with automatic local fallback cache
 * when Redis is offline or unreachable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "aigw:cache:";

    private final RedisTemplate<String, ChatResponse> redisTemplate;
    private final Map<String, ChatResponse> localFallbackCache = new ConcurrentHashMap<>();

    public Optional<ChatResponse> get(String cacheKey) {
        try {
            ChatResponse cached = redisTemplate.opsForValue().get(KEY_PREFIX + cacheKey);
            if (cached != null) {
                log.info("Cache HIT (Redis) for key={}", cacheKey);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed ({}), checking local fallback cache.", e.getMessage());
            ChatResponse fallback = localFallbackCache.get(cacheKey);
            if (fallback != null) {
                log.info("Cache HIT (Local Fallback) for key={}", cacheKey);
                return Optional.of(fallback);
            }
        }
        log.debug("Cache MISS for key={}", cacheKey);
        return Optional.empty();
    }

    public void put(String cacheKey, ChatResponse response) {
        localFallbackCache.put(cacheKey, response);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + cacheKey, response, TTL);
            log.info("Cached response in Redis for key={}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis cache write skipped: Redis unreachable ({})", e.getMessage());
        }
    }

    public void clear() {
        localFallbackCache.clear();
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        } catch (Exception e) {
            log.warn("Redis flushDb skipped: {}", e.getMessage());
        }
    }

    public String buildKey(String provider, String model, String messagesHash) {
        return String.format("%s:%s:%s", provider, model, messagesHash);
    }
}
