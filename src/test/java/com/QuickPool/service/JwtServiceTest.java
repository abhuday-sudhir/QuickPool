package com.QuickPool.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No mocks — JwtService has no dependencies to fake, just @Value fields normally injected
 * by Spring. ReflectionTestUtils sets them directly, the same trick DirectionsServiceTest uses
 * for its apiKey field (see TESTING.md).
 */
class JwtServiceTest {

    private JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "test-only-secret-at-least-32-bytes-long");
        ReflectionTestUtils.setField(service, "activeProfiles", "default");
        ReflectionTestUtils.setField(service, "accessTokenMinutes", 15L);
        ReflectionTestUtils.setField(service, "refreshTokenDays", 30L);
    }

    @Test
    @DisplayName("an access token round-trips its subject and reports type 'access'")
    void accessTokenRoundTrips() {
        UUID userId = UUID.randomUUID();
        String token = service.generateAccessToken(userId);

        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractUserId(token)).isEqualTo(userId);
        assertThat(service.extractType(token)).isEqualTo("access");
    }

    @Test
    @DisplayName("a refresh token round-trips its subject, jti, and reports type 'refresh'")
    void refreshTokenRoundTrips() {
        UUID userId = UUID.randomUUID();
        UUID jti = UUID.randomUUID();
        String token = service.generateRefreshToken(userId, jti);

        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractUserId(token)).isEqualTo(userId);
        assertThat(service.extractJti(token)).isEqualTo(jti);
        assertThat(service.extractType(token)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("isValid() is false for garbage, not an exception the caller has to catch")
    void invalidTokenIsFalseNotThrown() {
        assertThat(service.isValid("not-a-real-token")).isFalse();
    }

    @Test
    @DisplayName("a token signed with a different secret does not validate")
    void wrongSecretFailsValidation() {
        String token = service.generateAccessToken(UUID.randomUUID());

        JwtService otherService = new JwtService();
        ReflectionTestUtils.setField(otherService, "secret", "a-completely-different-secret-value-32bytes");
        ReflectionTestUtils.setField(otherService, "activeProfiles", "default");
        ReflectionTestUtils.setField(otherService, "accessTokenMinutes", 15L);
        ReflectionTestUtils.setField(otherService, "refreshTokenDays", 30L);

        assertThat(otherService.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("refuses to start with a secret shorter than 32 bytes (HS256's minimum)")
    void rejectsShortSecret() {
        JwtService shortSecretService = new JwtService();
        ReflectionTestUtils.setField(shortSecretService, "secret", "too-short");
        ReflectionTestUtils.setField(shortSecretService, "activeProfiles", "default");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(shortSecretService, "validateSecret"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refuses to start under the prod profile with the built-in development secret")
    void rejectsDevSecretInProd() {
        JwtService prodService = new JwtService();
        ReflectionTestUtils.setField(prodService, "secret",
                "dev-only-insecure-secret-min-32-chars-change-me");
        ReflectionTestUtils.setField(prodService, "activeProfiles", "prod");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(prodService, "validateSecret"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("allows the development secret outside the prod profile (just warns)")
    void allowsDevSecretOutsideProd() {
        JwtService devService = new JwtService();
        ReflectionTestUtils.setField(devService, "secret",
                "dev-only-insecure-secret-min-32-chars-change-me");
        ReflectionTestUtils.setField(devService, "activeProfiles", "default");

        ReflectionTestUtils.invokeMethod(devService, "validateSecret");
        // No exception is the assertion — reaching this line is the pass condition.
    }
}
