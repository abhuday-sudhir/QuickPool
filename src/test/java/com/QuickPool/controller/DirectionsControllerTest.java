package com.QuickPool.controller;

import com.QuickPool.dtos.DirectionsDto;
import com.QuickPool.service.DirectionsService;
import com.QuickPool.service.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The proxy exists to keep the Directions key off devices, so it must stay authenticated and capped. */
@WebMvcTest(DirectionsController.class)
class DirectionsControllerTest extends ControllerTestSupport {

    @MockitoBean private DirectionsService directionsService;
    @MockitoBean private RateLimiter rateLimiter;

    @Test
    @DisplayName("GET /directions returns the route for the given endpoints")
    void directions() throws Exception {
        when(rateLimiter.allow("directions", userId.toString(), 100, Duration.ofHours(1))).thenReturn(true);
        when(directionsService.route(28.6, 77.2, 28.5, 77.1))
                .thenReturn(new DirectionsDto("abc~poly", "12 km", "25 mins", 12000, 1500));

        mvc.perform(get("/api/v1/directions").with(authed())
                        .param("originLat", "28.6").param("originLng", "77.2")
                        .param("destLat", "28.5").param("destLng", "77.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polyline").value("abc~poly"))
                .andExpect(jsonPath("$.distanceMeters").value(12000))
                .andExpect(jsonPath("$.durationSeconds").value(1500));
    }

    @Test
    @DisplayName("over the hourly cap is a 429 and Google is not called")
    void directionsRateLimited() throws Exception {
        when(rateLimiter.allow("directions", userId.toString(), 100, Duration.ofHours(1))).thenReturn(false);

        mvc.perform(get("/api/v1/directions").with(authed())
                        .param("originLat", "28.6").param("originLng", "77.2")
                        .param("destLat", "28.5").param("destLng", "77.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many route requests. Try again shortly."));

        verifyNoInteractions(directionsService);
    }

    @Test
    @DisplayName("an anonymous caller cannot spend the Directions key")
    void directionsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/directions")
                        .param("originLat", "28.6").param("originLng", "77.2")
                        .param("destLat", "28.5").param("destLng", "77.1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directionsService, rateLimiter);
    }
}
