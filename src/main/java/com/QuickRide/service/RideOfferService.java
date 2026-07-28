package com.QuickRide.service;

import com.QuickRide.dtos.CreateRideOfferDto;
import com.QuickRide.dtos.RideOfferResponseDto;
import com.QuickRide.dtos.RideSearchRequestDto;
import com.QuickRide.entity.Booking;
import com.QuickRide.entity.RideOffer;
import com.QuickRide.enums.BookingStatus;
import com.QuickRide.enums.RideStatus;
import com.QuickRide.repository.BookingRepository;
import com.QuickRide.repository.RideOfferRepository;
import com.QuickRide.utils.GeoUtils;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RideOfferService {

    private static final double CORRIDOR_RADIUS_METERS = 2000; // tune later
    private static final int SEARCH_WINDOW_HOURS = 3;

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationService notificationService;

    public RideOfferResponseDto createRideOffer(CreateRideOfferDto dto, UUID driverId) {
        RideOffer offer = new RideOffer();
        offer.setDriverId(driverId);
        offer.setOriginLat(dto.getOriginLat());
        offer.setOriginLng(dto.getOriginLng());
        offer.setDestinationLat(dto.getDestinationLat());
        offer.setDestinationLng(dto.getDestinationLng());
        offer.setDepartureTime(dto.getDepartureTime());
        offer.setSeatsTotal(dto.getSeatsTotal());
        offer.setSeatsAvailable(dto.getSeatsTotal());
        offer.setPricePerSeat(dto.getPricePerSeat());
        offer.setStatus(RideStatus.ACTIVE);
        offer.setCreatedAt(LocalDateTime.now());
        offer.setUpdatedAt(LocalDateTime.now());
        return new RideOfferResponseDto(rideOfferRepository.save(offer));
    }

    public List<RideOfferResponseDto> search(RideSearchRequestDto req) {
        LocalDateTime from = req.getEarliestTime() != null
                ? req.getEarliestTime() : LocalDateTime.now();
        LocalDateTime to = req.getLatestTime() != null
                ? req.getLatestTime() : from.plusHours(SEARCH_WINDOW_HOURS);

        return rideOfferRepository.findByStatusAndDepartureTimeBetween(RideStatus.ACTIVE, from, to)
                .stream()
                .filter(r -> r.getSeatsAvailable() > 0)
                .filter(r -> withinCorridor(r, req.getPickupLat(), req.getPickupLng())
                        && withinCorridor(r, req.getDropLat(), req.getDropLng()))
                .map(RideOfferResponseDto::new)
                .collect(Collectors.toList());
    }

    private boolean withinCorridor(RideOffer r, double lat, double lng) {
        double dist = GeoUtils.distancePointToSegmentMeters(
                lat, lng, r.getOriginLat(), r.getOriginLng(),
                r.getDestinationLat(), r.getDestinationLng());
        return dist <= CORRIDOR_RADIUS_METERS;
    }

    @Transactional
    public void cancelRideOffer(UUID rideOfferId, UUID driverId) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(rideOfferId)
                .orElseThrow(() -> new IllegalArgumentException("Ride offer not found"));

        if (!offer.getDriverId().equals(driverId)) {
            throw new IllegalStateException("Not the owner of this ride");
        }

        offer.setStatus(RideStatus.CANCELLED);
        offer.setUpdatedAt(LocalDateTime.now());
        rideOfferRepository.save(offer);

        List<Booking> activeBookings =
                bookingRepository.findByRideOfferIdAndStatus(rideOfferId, BookingStatus.CONFIRMED);

        for (Booking b : activeBookings) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(b);
            notificationService.notifyUser(b.getPassengerId(), "Ride cancelled",
                    "The driver cancelled the ride you booked.");
        }
    }
}
