package com.QuickPool.service;

import com.QuickPool.dtos.SharedTripViewDto;
import com.QuickPool.dtos.TripShareDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.TripShare;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.TripShareRepository;
import com.QuickPool.repository.UserRepository;
import com.QuickPool.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** GET /api/v1/share/{token} is intentionally public — the person following a shared trip
 *  has no account — so view() must never leak anything beyond SharedTripViewDto's fields. */
@ExtendWith(MockitoExtension.class)
class TripShareServiceTest {

    @Mock private TripShareRepository shareRepository;
    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @InjectMocks private TripShareService service;

    private final UUID rideId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private final UUID passengerId = UUID.randomUUID();

    private RideOffer ride() {
        RideOffer r = new RideOffer();
        r.setId(rideId);
        r.setDriverId(driverId);
        r.setOriginLat(1.0); r.setOriginLng(2.0);
        r.setDestinationLat(3.0); r.setDestinationLng(4.0);
        r.setDepartureTime(LocalDateTime.now().plusHours(1));
        return r;
    }

    @Test
    @DisplayName("share() refuses someone who is neither the driver nor a confirmed passenger")
    void shareRefusesOutsider() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride()));
        when(bookingRepository.findByRideOfferIdAndStatusIn(eq(rideId), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.share(rideId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("share() reuses a still-live link instead of minting a new one")
    void shareReusesLiveLink() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride()));
        TripShare existing = new TripShare();
        existing.setToken("existing-token");
        existing.setExpiresAt(LocalDateTime.now().plusHours(1));
        existing.setRevoked(false);
        when(shareRepository.findByRideOfferIdAndSharedByAndRevokedFalse(rideId, driverId))
                .thenReturn(List.of(existing));

        TripShareDto result = service.share(rideId, driverId);

        assertThat(result.getToken()).isEqualTo("existing-token");
        verify(shareRepository, never()).save(any());
    }

    @Test
    @DisplayName("share() mints a fresh link for a confirmed passenger when none is live")
    void shareMintsFreshLinkForPassenger() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride()));
        Booking confirmed = new Booking();
        confirmed.setPassengerId(passengerId);
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByRideOfferIdAndStatusIn(eq(rideId), any())).thenReturn(List.of(confirmed));
        when(shareRepository.findByRideOfferIdAndSharedByAndRevokedFalse(rideId, passengerId))
                .thenReturn(List.of());
        when(shareRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TripShareDto result = service.share(rideId, passengerId);

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUrl()).endsWith(result.getToken());
        verify(shareRepository).save(any());
    }

    @Test
    @DisplayName("revoke() marks every live share this user made on this ride")
    void revokeMarksAllLiveShares() {
        TripShare a = new TripShare();
        TripShare b = new TripShare();
        when(shareRepository.findByRideOfferIdAndSharedByAndRevokedFalse(rideId, driverId))
                .thenReturn(List.of(a, b));

        service.revoke(rideId, driverId);

        assertThat(a.getRevoked()).isTrue();
        assertThat(b.getRevoked()).isTrue();
        verify(shareRepository).save(a);
        verify(shareRepository).save(b);
    }

    @Test
    @DisplayName("view() 404s for a token that never existed")
    void viewUnknownToken() {
        when(shareRepository.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view("nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("view() refuses a revoked link")
    void viewRevokedLink() {
        TripShare share = new TripShare();
        share.setRevoked(true);
        share.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(shareRepository.findByToken("t")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> service.view("t")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("view() refuses an expired link")
    void viewExpiredLink() {
        TripShare share = new TripShare();
        share.setRevoked(false);
        share.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(shareRepository.findByToken("t")).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> service.view("t")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("view() falls back to a generic driver name when none is set")
    void viewFallsBackToGenericDriverName() {
        TripShare share = new TripShare();
        share.setRideOfferId(rideId);
        share.setRevoked(false);
        share.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(shareRepository.findByToken("t")).thenReturn(Optional.of(share));
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride()));
        User driver = new User();
        driver.setName("  ");
        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByUserId(driverId)).thenReturn(Optional.empty());

        SharedTripViewDto result = service.view("t");

        assertThat(result.getDriverName()).isEqualTo("QuickPool driver");
        assertThat(result.getVehicle()).isNull();
    }
}
