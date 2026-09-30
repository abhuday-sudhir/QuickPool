package com.QuickPool.controller;

import com.QuickPool.dtos.GivenRatingDto;
import com.QuickPool.dtos.RateUserDto;
import com.QuickPool.dtos.RatingDto;
import com.QuickPool.dtos.ReportUserDto;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.service.RatingService;
import com.QuickPool.service.SafetyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SafetyController.class)
class SafetyControllerTest extends ControllerTestSupport {

    @MockitoBean private SafetyService safetyService;
    @MockitoBean private RatingService ratingService;

    private final UUID otherUser = UUID.randomUUID();
    private final UUID rideId = UUID.randomUUID();

    @Test
    @DisplayName("POST /ratings rates as the caller")
    void rate() throws Exception {
        mvc.perform(post("/api/v1/ratings").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s", "rateeId": "%s", "stars": 5, "comment": "Smooth ride"}"""
                                .formatted(rideId, otherUser)))
                .andExpect(status().isOk());

        ArgumentCaptor<RateUserDto> dto = ArgumentCaptor.forClass(RateUserDto.class);
        verify(ratingService).rate(eq(userId), dto.capture());
        assertThat(dto.getValue().getRateeId()).isEqualTo(otherUser);
        assertThat(dto.getValue().getStars()).isEqualTo((short) 5);
    }

    @Test
    @DisplayName("stars outside 1-5 fail validation")
    void rateOutOfRange() throws Exception {
        mvc.perform(post("/api/v1/ratings").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s", "rateeId": "%s", "stars": 6}"""
                                .formatted(rideId, otherUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stars").value("Rating must be 1-5"));

        verifyNoInteractions(ratingService);
    }

    @Test
    @DisplayName("rating the same person twice surfaces the service's 409")
    void rateTwice() throws Exception {
        doThrow(new ConflictException("Already rated")).when(ratingService).rate(eq(userId), any());

        mvc.perform(post("/api/v1/ratings").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s", "rateeId": "%s", "stars": 4}"""
                                .formatted(rideId, otherUser)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /ratings/mine lists ratings the caller has given")
    void myRatings() throws Exception {
        when(ratingService.ratingsGivenBy(userId))
                .thenReturn(List.of(new GivenRatingDto(rideId.toString(), otherUser.toString(), 4)));

        mvc.perform(get("/api/v1/ratings/mine").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rideOfferId").value(rideId.toString()))
                .andExpect(jsonPath("$[0].rateeId").value(otherUser.toString()))
                .andExpect(jsonPath("$[0].stars").value(4));
    }

    @Test
    @DisplayName("GET /users/{id}/ratings lists reviews for that user")
    void ratingsFor() throws Exception {
        when(ratingService.reviewsFor(otherUser))
                .thenReturn(List.of(new RatingDto(5, "Great", "Neha", "2030-01-01")));

        mvc.perform(get("/api/v1/users/{id}/ratings", otherUser).with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stars").value(5))
                .andExpect(jsonPath("$[0].raterName").value("Neha"));
    }

    @Test
    @DisplayName("POST /users/{id}/block blocks that user for the caller")
    void block() throws Exception {
        mvc.perform(post("/api/v1/users/{id}/block", otherUser).with(authed()))
                .andExpect(status().isOk());

        verify(safetyService).block(userId, otherUser);
    }

    @Test
    @DisplayName("DELETE /users/{id}/block unblocks that user for the caller")
    void unblock() throws Exception {
        mvc.perform(delete("/api/v1/users/{id}/block", otherUser).with(authed()))
                .andExpect(status().isOk());

        verify(safetyService).unblock(userId, otherUser);
    }

    @Test
    @DisplayName("GET /users/me/blocked lists blocked user ids")
    void blocked() throws Exception {
        when(safetyService.blockedIds(userId)).thenReturn(List.of(otherUser));

        mvc.perform(get("/api/v1/users/me/blocked").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(otherUser.toString()));
    }

    @Test
    @DisplayName("POST /reports files a report as the caller")
    void report() throws Exception {
        mvc.perform(post("/api/v1/reports").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedId": "%s", "rideOfferId": "%s", "reason": "UNSAFE_DRIVING", "details": "Speeding"}"""
                                .formatted(otherUser, rideId)))
                .andExpect(status().isOk());

        ArgumentCaptor<ReportUserDto> dto = ArgumentCaptor.forClass(ReportUserDto.class);
        verify(safetyService).report(eq(userId), dto.capture());
        assertThat(dto.getValue().getReportedId()).isEqualTo(otherUser);
        assertThat(dto.getValue().getReason()).isEqualTo("UNSAFE_DRIVING");
    }

    @Test
    @DisplayName("a report with no reason fails validation")
    void reportWithoutReason() throws Exception {
        mvc.perform(post("/api/v1/reports").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedId": "%s"}""".formatted(otherUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").value("Please choose a reason"));

        verifyNoInteractions(safetyService);
    }

    @Test
    @DisplayName("blocking needs a token")
    void unauthenticated() throws Exception {
        mvc.perform(post("/api/v1/users/{id}/block", otherUser))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(safetyService);
    }
}
