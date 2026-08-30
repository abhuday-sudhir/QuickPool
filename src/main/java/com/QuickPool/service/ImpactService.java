package com.QuickPool.service;

import com.QuickPool.dtos.ImpactDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.utils.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Estimates the environmental benefit of seats actually shared.
 *
 * Each shared seat means one person who would otherwise have driven the same route
 * did not, so the avoided emissions are that route's distance times a typical
 * per-car emission factor. These are estimates and are labelled as such in the app.
 */
@Service
public class ImpactService {

    /** kg CO2 per km for an average petrol car (UK DEFRA / EPA are both near this). */
    private static final double KG_CO2_PER_KM = 0.171;

    /** kg CO2 a mature tree absorbs in a year — the usual ~21 kg figure. */
    private static final double KG_CO2_PER_TREE_YEAR = 21.0;

    private static final List<BookingStatus> COUNTED =
            List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RideOfferRepository rideOfferRepository;

    public ImpactDto forUser(UUID userId) {
        Map<UUID, RideOffer> rideCache = new HashMap<>();

        // Seats this user took as a passenger.
        List<Booking> asPassenger = bookingRepository.findByPassengerIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(b -> COUNTED.contains(b.getStatus()))
                .collect(Collectors.toList());

        // Seats this user provided as a driver.
        List<UUID> myRideIds = rideOfferRepository.findByDriverId(userId).stream()
                .map(RideOffer::getId)
                .collect(Collectors.toList());
        List<Booking> asDriver = myRideIds.isEmpty()
                ? List.of()
                : bookingRepository.findByRideOfferIdInOrderByCreatedAtDesc(myRideIds).stream()
                        .filter(b -> COUNTED.contains(b.getStatus()))
                        .collect(Collectors.toList());

        int sharedRides = 0;
        double sharedKm = 0;

        for (Booking b : concat(asPassenger, asDriver)) {
            RideOffer ride = rideCache.computeIfAbsent(
                    b.getRideOfferId(), id -> rideOfferRepository.findById(id).orElse(null));
            if (ride == null) {
                continue;
            }
            double km = GeoUtils.haversineMeters(
                    ride.getOriginLat(), ride.getOriginLng(),
                    ride.getDestinationLat(), ride.getDestinationLng()) / 1000.0;
            int seats = b.getSeatsBooked() == null ? 1 : b.getSeatsBooked();
            sharedKm += km * seats;
            sharedRides++;
        }

        double co2 = sharedKm * KG_CO2_PER_KM;
        return new ImpactDto(
                sharedRides,
                round1(sharedKm),
                round1(co2),
                // Two decimals: early on this is a fraction of a tree, and rounding it
                // to a flat 0 would make the card look broken.
                round2(co2 / KG_CO2_PER_TREE_YEAR));
    }

    private static List<Booking> concat(List<Booking> a, List<Booking> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).collect(Collectors.toList());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
