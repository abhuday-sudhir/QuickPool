package com.QuickPool.controller;

import com.QuickPool.dtos.CreateRideOfferDto;
import com.QuickPool.dtos.PageResponseDto;
import com.QuickPool.dtos.RideOfferResponseDto;
import com.QuickPool.dtos.RideSearchRequestDto;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.service.RideOfferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RideOfferController.class)
class RideOfferControllerTest extends ControllerTestSupport {

    @MockitoBean private RideOfferService rideOfferService;

    private final UUID rideId = UUID.randomUUID();

    private RideOfferResponseDto response() {
        RideOffer r = new RideOffer();
        r.setId(rideId);
        r.setDriverId(userId);
        r.setOriginLat(28.61);
        r.setOriginLng(77.21);
        r.setDestinationLat(28.46);
        r.setDestinationLng(77.03);
        r.setDepartureTime(LocalDateTime.of(2099, 1, 1, 9, 0));
        r.setSeatsAvailable((short) 3);
        r.setPricePerSeat(new BigDecimal("150.00"));
        User driver = new User();
        driver.setName("Arjun");
        driver.setRatingAvg(new BigDecimal("4.8"));
        return new RideOfferResponseDto(r, driver, null);
    }

    @Test
    @DisplayName("POST /ride-offers creates a ride as the authenticated driver")
    void create() throws Exception {
        when(rideOfferService.createRideOffer(any(), eq(userId))).thenReturn(response());

        mvc.perform(post("/api/v1/ride-offers").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originLat": 28.61, "originLng": 77.21,
                                 "destinationLat": 28.46, "destinationLng": 77.03,
                                 "departureTime": "2099-01-01T09:00:00",
                                 "seatsTotal": 3, "pricePerSeat": 150.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rideId.toString()))
                .andExpect(jsonPath("$.driverId").value(userId.toString()))
                .andExpect(jsonPath("$.seatsAvailable").value(3))
                .andExpect(jsonPath("$.driverName").value("Arjun"))
                .andExpect(jsonPath("$.driverRating").value(4.8));

        ArgumentCaptor<CreateRideOfferDto> dto = ArgumentCaptor.forClass(CreateRideOfferDto.class);
        verify(rideOfferService).createRideOffer(dto.capture(), eq(userId));
        assertThat(dto.getValue().getSeatsTotal()).isEqualTo((short) 3);
        assertThat(dto.getValue().getDepartureTime()).isEqualTo(LocalDateTime.of(2099, 1, 1, 9, 0));
    }

    @Test
    @DisplayName("a ride in the past, out-of-range coordinates or too many seats fail validation")
    void createInvalid() throws Exception {
        mvc.perform(post("/api/v1/ride-offers").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originLat": 91.0, "originLng": 77.21,
                                 "destinationLat": 28.46,
                                 "departureTime": "2000-01-01T09:00:00",
                                 "seatsTotal": 9, "pricePerSeat": -1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.originLat").exists())
                .andExpect(jsonPath("$.fieldErrors.destinationLng").value("destinationLng is required"))
                .andExpect(jsonPath("$.fieldErrors.departureTime").value("departureTime must be in the future"))
                .andExpect(jsonPath("$.fieldErrors.seatsTotal").value("seatsTotal seems unreasonably high"))
                .andExpect(jsonPath("$.fieldErrors.pricePerSeat").value("pricePerSeat cannot be negative"));

        verifyNoInteractions(rideOfferService);
    }

    @Test
    @DisplayName("creating a ride needs a token")
    void createUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/ride-offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(rideOfferService);
    }

    @Test
    @DisplayName("POST /search passes the query and the viewer through")
    void search() throws Exception {
        when(rideOfferService.search(any(), eq(userId))).thenReturn(List.of(response()));

        mvc.perform(post("/api/v1/ride-offers/search").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLat": 28.60, "pickupLng": 77.20,
                                 "dropLat": 28.47, "dropLng": 77.04,
                                 "earliestTime": "2099-01-01T08:00:00",
                                 "latestTime": "2099-01-01T10:00:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(rideId.toString()));

        ArgumentCaptor<RideSearchRequestDto> dto = ArgumentCaptor.forClass(RideSearchRequestDto.class);
        verify(rideOfferService).search(dto.capture(), eq(userId));
        assertThat(dto.getValue().getPickupLat()).isEqualTo(28.60);
        assertThat(dto.getValue().getLatestTime()).isEqualTo(LocalDateTime.of(2099, 1, 1, 10, 0));
    }

    @Test
    @DisplayName("PUT /{id}/cancel cancels as the driver")
    void cancel() throws Exception {
        mvc.perform(put("/api/v1/ride-offers/{id}/cancel", rideId).with(authed()))
                .andExpect(status().isOk());

        verify(rideOfferService).cancelRideOffer(rideId, userId);
    }

    @Test
    @DisplayName("cancelling another driver's ride surfaces the service's 403")
    void cancelForbidden() throws Exception {
        doThrow(new ForbiddenException("Not your ride")).when(rideOfferService).cancelRideOffer(rideId, userId);

        mvc.perform(put("/api/v1/ride-offers/{id}/cancel", rideId).with(authed()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not your ride"));
    }

    @Test
    @DisplayName("PUT /{id}/start starts the ride as the driver")
    void start() throws Exception {
        mvc.perform(put("/api/v1/ride-offers/{id}/start", rideId).with(authed()))
                .andExpect(status().isOk());

        verify(rideOfferService).startRide(rideId, userId);
    }

    @Test
    @DisplayName("GET ?ids= batch-looks-up rides visible to the caller")
    void byIds() throws Exception {
        UUID other = UUID.randomUUID();
        when(rideOfferService.visibleByIds(List.of(rideId, other), userId)).thenReturn(List.of(response()));

        mvc.perform(get("/api/v1/ride-offers").with(authed()).param("ids", rideId + "," + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rideId.toString()));
    }

    @Test
    @DisplayName("GET /mine pages the driver's rides")
    void myRides() throws Exception {
        when(rideOfferService.getMyRides(eq(userId), any())).thenReturn(new PageResponseDto<>(List.of(response()), false));

        mvc.perform(get("/api/v1/ride-offers/mine").with(authed()).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.content[0].id").value(rideId.toString()));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(rideOfferService).getMyRides(eq(userId), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /{id}/passengers returns confirmed passengers in booking order")
    void passengerOrder() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(rideOfferService.getConfirmedPassengerOrder(rideId, userId)).thenReturn(List.of(first, second));

        mvc.perform(get("/api/v1/ride-offers/{id}/passengers", rideId).with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(first.toString()))
                .andExpect(jsonPath("$[1]").value(second.toString()));
    }

    @Test
    @DisplayName("passenger order for an unknown ride is a 404")
    void passengerOrderNotFound() throws Exception {
        when(rideOfferService.getConfirmedPassengerOrder(rideId, userId))
                .thenThrow(new NotFoundException("Ride not found"));

        mvc.perform(get("/api/v1/ride-offers/{id}/passengers", rideId).with(authed()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
