package com.QuickPool.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * A Redis fixed-window counter that must fail open (CLAUDE.md): if Redis itself is the
 * thing that's down, that must never be the reason auth stops working for everyone.
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private RateLimiter rateLimiter;

    @Test
    @DisplayName("allows a request within budget")
    void allowsWithinBudget() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:otp-phone:+9199900011")).thenReturn(3L);

        boolean allowed = rateLimiter.allow("otp-phone", "+9199900011", 5, Duration.ofHours(1));

        assertThat(allowed).isTrue();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("sets the window's expiry only on the first request in it")
    void setsExpiryOnlyOnFirstHit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:otp-phone:+9199900011")).thenReturn(1L);

        rateLimiter.allow("otp-phone", "+9199900011", 5, Duration.ofHours(1));

        verify(redis).expire("rl:otp-phone:+9199900011", Duration.ofHours(1));
    }

    @Test
    @DisplayName("refuses once the count exceeds the limit")
    void refusesOverBudget() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:otp-phone:+9199900011")).thenReturn(6L);

        boolean allowed = rateLimiter.allow("otp-phone", "+9199900011", 5, Duration.ofHours(1));

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("fails open when Redis itself is unreachable")
    void failsOpenOnRedisError() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        boolean allowed = rateLimiter.allow("otp-phone", "+9199900011", 5, Duration.ofHours(1));

        assertThat(allowed).isTrue();
    }
}
