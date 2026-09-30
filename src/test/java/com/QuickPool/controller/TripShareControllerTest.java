package com.QuickPool.controller;

import com.QuickPool.dtos.SharedTripViewDto;
import com.QuickPool.dtos.TripShareDto;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.service.TripShareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Creating and revoking a share link needs an account; following one must not, because the
 * recipient is not a QuickPool user. Only GET on /share/* is opened up in SecurityConfig.
 */
@WebMvcTest(TripShareController.class)
class TripShareControllerTest extends ControllerTestSupport {

    @MockitoBean private TripShareService tripShareService;

    private final UUID rideId = UUID.randomUUID();

    @Test
    @DisplayName("POST /rides/{id}/share creates a link for the caller")
    void share() throws Exception {
        when(tripShareService.share(rideId, userId)).thenReturn(
                new TripShareDto("tok123", "https://quickpool.app/share/tok123", LocalDateTime.of(2030, 1, 1, 12, 0)));

        mvc.perform(post("/api/v1/rides/{rideId}/share", rideId).with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok123"))
                .andExpect(jsonPath("$.url").value("https://quickpool.app/share/tok123"));
    }

    @Test
    @DisplayName("creating a share link needs a token")
    void shareUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/rides/{rideId}/share", rideId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tripShareService);
    }

    @Test
    @DisplayName("DELETE /rides/{id}/share revokes the caller's link")
    void revoke() throws Exception {
        mvc.perform(delete("/api/v1/rides/{rideId}/share", rideId).with(authed()))
                .andExpect(status().isOk());

        verify(tripShareService).revoke(rideId, userId);
    }

    @Test
    @DisplayName("GET /share/{token} is viewable without an account")
    void viewAnonymously() throws Exception {
        when(tripShareService.view("tok123")).thenReturn(new SharedTripViewDto(
                "IN_PROGRESS", "Arjun", "White Maruti Swift · DL3CAB1234",
                28.61, 77.21, 28.46, 77.03, LocalDateTime.of(2030, 1, 1, 9, 0),
                28.55, 77.10, LocalDateTime.of(2030, 1, 1, 9, 20)));

        mvc.perform(get("/api/v1/share/{token}", "tok123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.driverName").value("Arjun"))
                .andExpect(jsonPath("$.lastLat").value(28.55));
    }

    @Test
    @DisplayName("an expired or revoked link is a 404")
    void viewUnknown() throws Exception {
        when(tripShareService.view("gone")).thenThrow(new NotFoundException("This link has expired"));

        mvc.perform(get("/api/v1/share/{token}", "gone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This link has expired"));
    }
}
