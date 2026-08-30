package com.QuickPool.service;

import com.QuickPool.dtos.RateUserDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RatingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private RatingService service;

    private final UUID driver = UUID.randomUUID();
    private final UUID passenger = UUID.randomUUID();
    private final UUID rideId = UUID.randomUUID();
    private RideOffer ride;

    @BeforeEach
    void setUp() {
        ride = new RideOffer();
        ride.setId(rideId);
        ride.setDriverId(driver);
        ride.setStatus(RideStatus.IN_PROGRESS);
    }

    private RateUserDto dto(UUID ratee, short stars) {
        RateUserDto d = new RateUserDto();
        d.setRideOfferId(rideId);
        d.setRateeId(ratee);
        d.setStars(stars);
        return d;
    }

    private void ridersOnBoard() {
        Booking b = new Booking();
        b.setPassengerId(passenger);
        when(bookingRepository.findByRideOfferIdAndStatusIn(eq(rideId), any()))
                .thenReturn(List.of(b));
    }

    @Test
    @DisplayName("rejects rating yourself")
    void rejectsSelfRating() {
        assertThatThrownBy(() -> service.rate(driver, dto(driver, (short) 5)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(ratingRepository);
    }

    @Test
    @DisplayName("rejects rating before the ride has started")
    void rejectsBeforeRideStarts() {
        ride.setStatus(RideStatus.ACTIVE);
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> service.rate(driver, dto(passenger, (short) 5)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("rejects rating someone who was not on the ride")
    void rejectsStranger() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(bookingRepository.findByRideOfferIdAndStatusIn(eq(rideId), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.rate(driver, dto(passenger, (short) 5)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("rejects a second rating for the same ride")
    void rejectsDuplicate() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));
        ridersOnBoard();
        when(ratingRepository.existsByRideOfferIdAndRaterIdAndRateeId(rideId, driver, passenger))
                .thenReturn(true);

        assertThatThrownBy(() -> service.rate(driver, dto(passenger, (short) 5)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("saves the rating and refreshes the average on the user")
    void savesAndRecomputesAverage() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));
        ridersOnBoard();
        when(ratingRepository.existsByRideOfferIdAndRaterIdAndRateeId(rideId, driver, passenger))
                .thenReturn(false);
        when(ratingRepository.averageFor(passenger)).thenReturn(4.5);

        User rated = new User();
        rated.setId(passenger);
        when(userRepository.findById(passenger)).thenReturn(Optional.of(rated));

        service.rate(driver, dto(passenger, (short) 4));

        verify(ratingRepository).save(any());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRatingAvg()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("a passenger may rate the driver too")
    void passengerRatesDriver() {
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride));
        ridersOnBoard();
        when(ratingRepository.averageFor(driver)).thenReturn(5.0);
        User d = new User();
        d.setId(driver);
        when(userRepository.findById(driver)).thenReturn(Optional.of(d));

        RateUserDto d2 = new RateUserDto();
        d2.setRideOfferId(rideId);
        d2.setRateeId(driver);
        d2.setStars((short) 5);

        service.rate(passenger, d2);

        verify(ratingRepository).save(any());
    }
}
