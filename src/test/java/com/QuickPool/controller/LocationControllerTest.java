package com.QuickPool.controller;

import com.QuickPool.dtos.LocationBroadcastDto;
import com.QuickPool.dtos.LocationUpdateDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.QuickPool.enums.BookingStatus.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The STOMP location channel. A plain unit test rather than a web slice: the handler is only
 * reachable over a WebSocket session, and the userId it trusts is the one JwtHandshakeInterceptor
 * put into the session attributes at handshake time.
 */
@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @InjectMocks private LocationController controller;

    private final UUID rideId = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID passenger = UUID.randomUUID();
    private RideOffer ride;

    @BeforeEach
    void setUp() {
        ride = new RideOffer();
        ride.setId(rideId);
        ride.setDriverId(driver);
        ride.setStatus(RideStatus.IN_PROGRESS);
    }

    private SimpMessageHeaderAccessor sessionFor(UUID userId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        accessor.setSessionAttributes(attrs);
        return accessor;
    }

    private LocationUpdateDto at(double lat, double lng) {
        LocationUpdateDto dto = new LocationUpdateDto();
        dto.setLat(lat);
        dto.setLng(lng);
        return dto;
    }

    private Booking confirmedBookingFor(UUID passengerId) {
        Booking b = new Booking();
        b.setRideOfferId(rideId);
        b.setPassengerId(passengerId);
        b.setStatus(CONFIRMED);
        return b;
    }

    @Test
    @DisplayName("the driver's fix is stored on the ride and broadcast with the DRIVER role")
    void driverUpdate() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));

        controller.updateLocation(rideId, at(28.55, 77.10), sessionFor(driver));

        assertThat(ride.getLastLat()).isEqualTo(28.55);
        assertThat(ride.getLastLng()).isEqualTo(77.10);
        assertThat(ride.getLastLocationAt()).isNotNull();
        verify(rideOfferRepository).save(ride);
        verifyNoInteractions(bookingRepository);

        ArgumentCaptor<LocationBroadcastDto> sent = ArgumentCaptor.forClass(LocationBroadcastDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/ride/" + rideId + "/location"), sent.capture());
        assertThat(sent.getValue().getUserId()).isEqualTo(driver);
        assertThat(sent.getValue().getRole()).isEqualTo("DRIVER");
        assertThat(sent.getValue().getLat()).isEqualTo(28.55);
        assertThat(sent.getValue().getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("a confirmed passenger's fix is broadcast as PASSENGER but not stored")
    void passengerUpdate() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(bookingRepository.findByRideOfferIdAndStatus(rideId, CONFIRMED))
                .thenReturn(List.of(confirmedBookingFor(UUID.randomUUID()), confirmedBookingFor(passenger)));

        controller.updateLocation(rideId, at(28.56, 77.11), sessionFor(passenger));

        verify(rideOfferRepository, never()).save(any());
        assertThat(ride.getLastLat()).isNull();

        ArgumentCaptor<LocationBroadcastDto> sent = ArgumentCaptor.forClass(LocationBroadcastDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/ride/" + rideId + "/location"), sent.capture());
        assertThat(sent.getValue().getUserId()).isEqualTo(passenger);
        assertThat(sent.getValue().getRole()).isEqualTo("PASSENGER");
    }

    @Test
    @DisplayName("someone with no confirmed booking cannot publish on the ride's channel")
    void strangerRejected() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(bookingRepository.findByRideOfferIdAndStatus(rideId, CONFIRMED))
                .thenReturn(List.of(confirmedBookingFor(passenger)));

        assertThatThrownBy(() -> controller.updateLocation(rideId, at(1, 1), sessionFor(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authorized for this ride's location channel");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("location sharing is refused unless the ride is in progress")
    void rideNotInProgress() {
        ride.setStatus(RideStatus.ACTIVE);
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> controller.updateLocation(rideId, at(1, 1), sessionFor(driver)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ride is not currently active for location sharing");

        verify(rideOfferRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("an unknown ride id is rejected")
    void rideNotFound() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateLocation(rideId, at(1, 1), sessionFor(driver)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ride not found");

        verifyNoInteractions(messagingTemplate);
    }
}
