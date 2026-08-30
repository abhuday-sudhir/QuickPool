package com.QuickPool.service;

import com.QuickPool.dtos.SharedTripViewDto;
import com.QuickPool.dtos.TripShareDto;
import com.QuickPool.dtos.VehicleDto;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.TripShare;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.TripShareRepository;
import com.QuickPool.repository.UserRepository;
import com.QuickPool.repository.VehicleRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class TripShareService {

    private static final int SHARE_HOURS = 12;
    private static final List<BookingStatus> ON_BOARD =
            List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private final SecureRandom random = new SecureRandom();

    @Value("${app.share.base-url:http://localhost:8080/t/}")
    private String shareBaseUrl;

    @Autowired
    private TripShareRepository shareRepository;

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    /** Anyone actually on the ride — driver or confirmed passenger — may share it. */
    @Transactional
    public TripShareDto share(UUID rideId, UUID userId) {
        RideOffer ride = rideOfferRepository.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        boolean onBoard = ride.getDriverId().equals(userId)
                || bookingRepository.findByRideOfferIdAndStatusIn(rideId, ON_BOARD).stream()
                        .anyMatch(b -> b.getPassengerId().equals(userId));
        if (!onBoard) {
            throw new ForbiddenException("You are not on this ride");
        }

        // Reuse a live link rather than minting one per tap.
        var existing = shareRepository.findByRideOfferIdAndSharedByAndRevokedFalse(rideId, userId).stream()
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .findFirst();
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        TripShare share = new TripShare();
        share.setToken(newToken());
        share.setRideOfferId(rideId);
        share.setSharedBy(userId);
        share.setExpiresAt(LocalDateTime.now().plusHours(SHARE_HOURS));
        share.setRevoked(false);
        share.setCreatedAt(LocalDateTime.now());
        return toDto(shareRepository.save(share));
    }

    @Transactional
    public void revoke(UUID rideId, UUID userId) {
        shareRepository.findByRideOfferIdAndSharedByAndRevokedFalse(rideId, userId)
                .forEach(s -> {
                    s.setRevoked(true);
                    shareRepository.save(s);
                });
    }

    /** Public read. Never returns anything identifying the passengers. */
    public SharedTripViewDto view(String token) {
        TripShare share = shareRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("This link is not valid"));
        if (Boolean.TRUE.equals(share.getRevoked()) || share.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new NotFoundException("This link has expired");
        }

        RideOffer ride = rideOfferRepository.findById(share.getRideOfferId())
                .orElseThrow(() -> new NotFoundException("Ride not found"));

        String driverName = userRepository.findById(ride.getDriverId())
                .map(u -> u.getName() == null || u.getName().isBlank() ? "QuickPool driver" : u.getName())
                .orElse("QuickPool driver");
        String vehicle = vehicleRepository.findByUserId(ride.getDriverId())
                .map(v -> new VehicleDto(v).describe())
                .orElse(null);

        return new SharedTripViewDto(
                ride.getStatus().name(),
                driverName,
                vehicle,
                ride.getOriginLat(), ride.getOriginLng(),
                ride.getDestinationLat(), ride.getDestinationLng(),
                ride.getDepartureTime(),
                ride.getLastLat(), ride.getLastLng(), ride.getLastLocationAt());
    }

    private TripShareDto toDto(TripShare s) {
        return new TripShareDto(s.getToken(), shareBaseUrl + s.getToken(), s.getExpiresAt());
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
