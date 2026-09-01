package com.QuickPool.service;

import com.QuickPool.dtos.GivenRatingDto;
import com.QuickPool.dtos.RateUserDto;
import com.QuickPool.dtos.RatingDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.Rating;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RatingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RatingService {

    private static final List<BookingStatus> RODE_TOGETHER =
            List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * You may only rate someone you actually shared a specific ride with, once,
     * and only after that ride has started.
     */
    @Transactional
    public void rate(UUID raterId, RateUserDto dto) {
        if (raterId.equals(dto.getRateeId())) {
            throw new ForbiddenException("You cannot rate yourself");
        }

        RideOffer ride = rideOfferRepository.findById(dto.getRideOfferId())
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        if (ride.getStatus() != RideStatus.IN_PROGRESS && ride.getStatus() != RideStatus.COMPLETED) {
            throw new ConflictException("You can rate once the ride has started");
        }
        if (!sharedRide(ride, raterId, dto.getRateeId())) {
            throw new ForbiddenException("You did not share this ride with that person");
        }
        if (ratingRepository.existsByRideOfferIdAndRaterIdAndRateeId(
                ride.getId(), raterId, dto.getRateeId())) {
            throw new ConflictException("You have already rated them for this ride");
        }

        Rating rating = new Rating();
        rating.setRideOfferId(ride.getId());
        rating.setRaterId(raterId);
        rating.setRateeId(dto.getRateeId());
        rating.setStars(dto.getStars());
        rating.setComment(dto.getComment() == null || dto.getComment().isBlank()
                ? null : dto.getComment().trim());
        rating.setCreatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        recomputeAverage(dto.getRateeId());
    }

    /** Every rating this user has handed out — the app uses it to disable "Rate". */
    public List<GivenRatingDto> ratingsGivenBy(UUID raterId) {
        return ratingRepository.findByRaterId(raterId).stream()
                .map(r -> new GivenRatingDto(
                        r.getRideOfferId().toString(), r.getRateeId().toString(), r.getStars()))
                .collect(Collectors.toList());
    }

    public List<RatingDto> reviewsFor(UUID userId) {
        return ratingRepository.findByRateeIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> new RatingDto(
                        r.getStars(),
                        r.getComment(),
                        userRepository.findById(r.getRaterId())
                                .map(u -> u.getName() == null || u.getName().isBlank() ? "A rider" : u.getName())
                                .orElse("A rider"),
                        r.getCreatedAt() == null ? null : r.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }

    /** True if one of them drove the ride and the other held a real seat on it. */
    private boolean sharedRide(RideOffer ride, UUID a, UUID b) {
        UUID driver = ride.getDriverId();
        UUID passenger;
        if (driver.equals(a)) {
            passenger = b;
        } else if (driver.equals(b)) {
            passenger = a;
        } else {
            return false; // passenger-to-passenger rating is not supported
        }
        return bookingRepository.findByRideOfferIdAndStatusIn(ride.getId(), RODE_TOGETHER).stream()
                .map(Booking::getPassengerId)
                .anyMatch(passenger::equals);
    }

    private void recomputeAverage(UUID userId) {
        Double avg = ratingRepository.averageFor(userId);
        userRepository.findById(userId).ifPresent(u -> {
            u.setRatingAvg(avg == null ? null
                    : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            userRepository.save(u);
        });
    }
}
