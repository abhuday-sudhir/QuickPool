package com.QuickPool.service;

import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The 15-minute sweep (CLAUDE.md) that ages rides nothing else ever moved out of ACTIVE/
 * IN_PROGRESS. Both halves matter independently: expiring a ride nobody started must also
 * release the passengers waiting on it, and completing a stale ride is what unlocks rating.
 */
@ExtendWith(MockitoExtension.class)
class RideLifecycleServiceTest {

    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private RideLifecycleService service;

    private RideOffer ride(RideStatus status) {
        RideOffer r = new RideOffer();
        r.setId(UUID.randomUUID());
        r.setStatus(status);
        return r;
    }

    @Test
    @DisplayName("expires a ride departed hours ago and was never started, cancelling its bookings")
    void expiresUnstartedRide() {
        RideOffer stale = ride(RideStatus.ACTIVE);
        when(rideOfferRepository.findByStatusInAndDepartureTimeBefore(
                eq(List.of(RideStatus.ACTIVE, RideStatus.FULL)), any()))
                .thenReturn(List.of(stale));
        when(rideOfferRepository.findByStatusInAndDepartureTimeBefore(
                eq(List.of(RideStatus.IN_PROGRESS)), any()))
                .thenReturn(List.of());

        Booking pending = new Booking();
        pending.setPassengerId(UUID.randomUUID());
        pending.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findByRideOfferIdAndStatusIn(
                eq(stale.getId()), eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))))
                .thenReturn(List.of(pending));

        service.sweep();

        assertThat(stale.getStatus()).isEqualTo(RideStatus.EXPIRED);
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(rideOfferRepository).save(stale);
        verify(bookingRepository).save(pending);
        verify(notificationService).notifyUser(eq(pending.getPassengerId()), any(), any(),
                eq(NotificationType.RIDE_CANCELLED), eq(stale.getId()));
    }

    @Test
    @DisplayName("completes a ride left IN_PROGRESS for hours, completing its confirmed bookings")
    void completesStaleRide() {
        when(rideOfferRepository.findByStatusInAndDepartureTimeBefore(
                eq(List.of(RideStatus.ACTIVE, RideStatus.FULL)), any()))
                .thenReturn(List.of());
        RideOffer running = ride(RideStatus.IN_PROGRESS);
        when(rideOfferRepository.findByStatusInAndDepartureTimeBefore(
                eq(List.of(RideStatus.IN_PROGRESS)), any()))
                .thenReturn(List.of(running));

        Booking confirmed = new Booking();
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByRideOfferIdAndStatusIn(
                eq(running.getId()), eq(List.of(BookingStatus.CONFIRMED))))
                .thenReturn(List.of(confirmed));

        service.sweep();

        assertThat(running.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(rideOfferRepository).save(running);
        verify(bookingRepository).save(confirmed);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("does nothing when there is nothing stale")
    void noopWhenNothingStale() {
        when(rideOfferRepository.findByStatusInAndDepartureTimeBefore(any(), any())).thenReturn(List.of());

        service.sweep();

        verify(rideOfferRepository, never()).save(any());
        verifyNoInteractions(bookingRepository, notificationService);
    }
}
