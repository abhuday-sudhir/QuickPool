package com.QuickPool.service;

import com.QuickPool.dtos.CreateRideOfferDto;
import com.QuickPool.dtos.RideOfferResponseDto;
import com.QuickPool.dtos.RideSearchRequestDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.entity.Vehicle;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.dtos.PageResponseDto;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.UserRepository;
import com.QuickPool.repository.VehicleRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private SafetyService safetyService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ActivityLogService activityLogService;

    public RideOfferResponseDto createRideOffer(CreateRideOfferDto dto, UUID driverId) {
        if (dto.getDepartureTime() == null || dto.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new ConflictException("Departure time must be in the future");
        }
        if (dto.getSeatsTotal() == null || dto.getSeatsTotal() < 1) {
            throw new ConflictException("A ride must offer at least one seat");
        }

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

        RideOffer save = rideOfferRepository.save(offer);
        activityLogService.log(driverId, "RIDE_CREATED", "RIDE_OFFER", offer.getId(), null);
        return new RideOfferResponseDto(save);
    }

    /**
     * @param viewerId the searching user, whose own rides are never returned
     *
     * The corridor check (both pickup and drop within {@link #CORRIDOR_RADIUS_METERS} of the
     * ride's origin-destination line) runs in PostGIS via {@code searchCorridor}, not in Java —
     * see the GiST index on {@code ride_offers.route} (migration V10). Only the block-list
     * check stays here: it is a per-viewer set, not something worth pushing into every search
     * query.
     */
    public List<RideOfferResponseDto> search(RideSearchRequestDto req, UUID viewerId) {
        LocalDateTime from = req.getEarliestTime() != null
                ? req.getEarliestTime() : LocalDateTime.now();
        LocalDateTime to = req.getLatestTime() != null
                ? req.getLatestTime() : from.plusHours(SEARCH_WINDOW_HOURS);

        // Blocked in either direction: their rides must not surface for this viewer.
        var hidden = safetyService.hiddenFrom(viewerId);

        var matches = rideOfferRepository.searchCorridor(
                        from, to, viewerId,
                        req.getPickupLat(), req.getPickupLng(),
                        req.getDropLat(), req.getDropLng(),
                        CORRIDOR_RADIUS_METERS)
                .stream()
                .filter(r -> !hidden.contains(r.getDriverId()))
                .toList();

        // One lookup for every driver on the page instead of one per ride.
        var driverIds = matches.stream().map(RideOffer::getDriverId).distinct().toList();
        var drivers = userRepository.findAllById(driverIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        var vehicles = vehicleRepository.findByUserIdIn(driverIds).stream()
                .collect(Collectors.toMap(Vehicle::getUserId, v -> v));

        return matches.stream()
                .map(r -> new RideOfferResponseDto(
                        r,
                        drivers.get(r.getDriverId()),
                        vehicles.get(r.getDriverId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelRideOffer(UUID rideOfferId, UUID driverId) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(rideOfferId)
                .orElseThrow(() -> new NotFoundException("Ride offer not found"));

        if (!offer.getDriverId().equals(driverId)) {
            throw new ForbiddenException("Not the owner of this ride");
        }

        offer.setStatus(RideStatus.CANCELLED);
        offer.setUpdatedAt(LocalDateTime.now());
        rideOfferRepository.save(offer);

        // Both confirmed seats and still-pending requests die with the ride.
        List<Booking> activeBookings = bookingRepository.findByRideOfferIdAndStatusIn(
                rideOfferId, List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING));

        activityLogService.log(driverId, "RIDE_CANCELLED", "RIDE_OFFER", rideOfferId, null);

        for (Booking b : activeBookings) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(b);
            notificationService.notifyUser(b.getPassengerId(), "Ride cancelled",
                    "The driver cancelled the ride you booked.",
                    NotificationType.RIDE_CANCELLED, rideOfferId);
        }
    }

    @Transactional
    public void startRide(UUID rideOfferId, UUID driverId) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(rideOfferId)
                .orElseThrow(() -> new NotFoundException("Ride offer not found"));

        if (!offer.getDriverId().equals(driverId)) {
            throw new ForbiddenException("Not the owner of this ride");
        }
        if (offer.getStatus() != RideStatus.ACTIVE && offer.getStatus() != RideStatus.FULL) {
            throw new ConflictException("Ride cannot be started from its current state");
        }

        offer.setStatus(RideStatus.IN_PROGRESS);
        offer.setUpdatedAt(LocalDateTime.now());
        rideOfferRepository.save(offer);
        activityLogService.log(driverId, "RIDE_STARTED", "RIDE_OFFER", rideOfferId, null);

        for (Booking b : bookingRepository.findByRideOfferIdAndStatus(rideOfferId, BookingStatus.CONFIRMED)) {
            notificationService.notifyUser(b.getPassengerId(), "Ride started",
                    "Your driver has started the ride. You can track them live now.",
                    NotificationType.RIDE_STARTED, rideOfferId);
        }
    }

    /**
     * Look up specific rides, but only ones the caller is actually part of — either
     * they drive it or they hold a booking on it. Stops ride ids being enumerated
     * for other people's driver details.
     */
    public List<RideOfferResponseDto> visibleByIds(List<UUID> ids, UUID viewerId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var involvedRideIds = bookingRepository.findByPassengerId(viewerId).stream()
                .map(Booking::getRideOfferId)
                .collect(Collectors.toSet());

        var rides = rideOfferRepository.findAllById(ids).stream()
                .filter(r -> r.getDriverId().equals(viewerId) || involvedRideIds.contains(r.getId()))
                .toList();

        var driverIds = rides.stream().map(RideOffer::getDriverId).distinct().toList();
        var drivers = userRepository.findAllById(driverIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        var vehicles = vehicleRepository.findByUserIdIn(driverIds).stream()
                .collect(Collectors.toMap(Vehicle::getUserId, v -> v));

        return rides.stream()
                .map(r -> new RideOfferResponseDto(r, drivers.get(r.getDriverId()), vehicles.get(r.getDriverId())))
                .collect(Collectors.toList());
    }

    public PageResponseDto<RideOfferResponseDto> getMyRides(UUID driverId, Pageable pageable) {
        Slice<RideOffer> slice = rideOfferRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageable);
        var content = slice.getContent().stream().map(RideOfferResponseDto::new).toList();
        return new PageResponseDto<>(content, slice.hasNext());
    }

    /**
     * Stable seat order for a ride's confirmed passengers, oldest booking first. Every
     * client on the live-location socket (driver and passengers alike) calls this once
     * so a given passenger renders under the same number — "2" — on every screen.
     * Restricted to people actually on the ride: its driver, or one of these passengers.
     */
    public List<UUID> getConfirmedPassengerOrder(UUID rideOfferId, UUID requesterId) {
        RideOffer offer = rideOfferRepository.findById(rideOfferId)
                .orElseThrow(() -> new NotFoundException("Ride offer not found"));

        List<UUID> passengerIds = bookingRepository
                .findByRideOfferIdAndStatusOrderByCreatedAtAsc(rideOfferId, BookingStatus.CONFIRMED)
                .stream()
                .map(Booking::getPassengerId)
                .toList();

        boolean requesterInvolved = offer.getDriverId().equals(requesterId)
                || passengerIds.contains(requesterId);
        if (!requesterInvolved) {
            throw new ForbiddenException("Not part of this ride");
        }

        return passengerIds;
    }
}
