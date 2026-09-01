package com.QuickPool.service;

import com.QuickPool.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the guards that run before any billed call goes out. The Google round-trip itself
 * is left to end-to-end checking — mocking it would only assert our own JSON parsing back.
 */
class DirectionsServiceTest {

    private DirectionsService serviceWithKey(String key) {
        DirectionsService service = new DirectionsService();
        ReflectionTestUtils.setField(service, "apiKey", key);
        return service;
    }

    @Test
    @DisplayName("out-of-range coordinates are rejected before the key is even read")
    void rejectsBadCoordinates() {
        // No key set: reaching the key check would throw IllegalStateException instead,
        // which is exactly what this asserts does not happen.
        DirectionsService service = serviceWithKey("");

        assertThatThrownBy(() -> service.route(91.0, 77.0, 12.9, 77.6))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("originLat");

        assertThatThrownBy(() -> service.route(12.9, 181.0, 12.9, 77.6))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("originLng");

        assertThatThrownBy(() -> service.route(12.9, 77.6, -91.0, 77.6))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("destLat");

        assertThatThrownBy(() -> service.route(12.9, 77.6, 12.9, -181.0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("destLng");
    }

    @Test
    @DisplayName("an unconfigured server fails loudly rather than calling Google keyless")
    void requiresKey() {
        DirectionsService service = serviceWithKey("");

        assertThatThrownBy(() -> service.route(12.9716, 77.5946, 12.9352, 77.6245))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
