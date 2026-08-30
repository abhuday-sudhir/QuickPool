package com.QuickPool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fixed-window counter in Redis. Auth endpoints are the ones worth protecting:
 * once OTP delivery costs money per message, an unthrottled request endpoint is
 * somebody else's bill.
 *
 * Fails open — if Redis is unreachable we allow the request rather than locking
 * everyone out of login.
 */
@Service
@Slf4j
public class RateLimiter {

    @Autowired
    private StringRedisTemplate redis;

    /** @return true when the caller is within budget */
    public boolean allow(String scope, String key, int limit, Duration window) {
        String redisKey = "rl:" + scope + ":" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            boolean allowed = count == null || count <= limit;
            if (!allowed) {
                log.warn("Rate limit hit for {} {}", scope, key);
            }
            return allowed;
        } catch (Exception e) {
            log.warn("Rate limiter unavailable ({}), allowing request: {}", scope, e.getMessage());
            return true;
        }
    }
}
