package com.QuickPool.service;

import com.QuickPool.dtos.ImpactDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpactServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private RideOfferRepository rideOfferRepository;
    @InjectMocks private ImpactService service;

    private final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("no shared seats means a clean zero, not a broken card")
    void zeroState() {
        when(bookingRepository.findByPassengerIdOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(rideOfferRepository.findByDriverId(user)).thenReturn(List.of());

        ImpactDto impact = service.forUser(user);

        assertThat(impact.getSharedRides()).isZero();
        assertThat(impact.getSharedKm()).isZero();
    }

    @Test
    @DisplayName("cancelled bookings do not count towards impact")
    void ignoresCancelled() {
        UUID rideId = UUID.randomUUID();
        Booking cancelled = new Booking();
        cancelled.setRideOfferId(rideId);
        cancelled.setStatus(BookingStatus.CANCELLED);
        cancelled.setSeatsBooked((short) 1);

        when(bookingRepository.findByPassengerIdOrderByCreatedAtDesc(user)).thenReturn(List.of(cancelled));
        when(rideOfferRepository.findByDriverId(user)).thenReturn(List.of());

        assertThat(service.forUser(user).getSharedRides()).isZero();
    }

    @Test
    @DisplayName("CO2 and trees follow the documented factors")
    void appliesEmissionFactors() {
        UUID rideId = UUID.randomUUID();

        RideOffer ride = new RideOffer();
        ride.setId(rideId);
        // ~111 km apart: one degree of latitude
        ride.setOriginLat(28.0);
        ride.setOriginLng(77.0);
        ride.setDestinationLat(29.0);
        ride.setDestinationLng(77.0);

        Booking confirmed = new Booking();
        confirmed.setRideOfferId(rideId);
        confirmed.setStatus(BookingStatus.CONFIRMED);
        confirmed.setSeatsBooked((short) 1);

        when(bookingRepository.findByPassengerIdOrderByCreatedAtDesc(user)).thenReturn(List.of(confirmed));
        when(rideOfferRepository.findByDriverId(user)).thenReturn(List.of());
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));

        ImpactDto impact = service.forUser(user);

        assertThat(impact.getSharedRides()).isEqualTo(1);
        assertThat(impact.getSharedKm()).isCloseTo(111.2, org.assertj.core.data.Offset.offset(1.0));
        assertThat(impact.getCo2SavedKg())
                .isCloseTo(impact.getSharedKm() * 0.171, org.assertj.core.data.Offset.offset(0.2));
        assertThat(impact.getTreesEquivalent())
                .isCloseTo(impact.getCo2SavedKg() / 21.0, org.assertj.core.data.Offset.offset(0.05));
    }
}
