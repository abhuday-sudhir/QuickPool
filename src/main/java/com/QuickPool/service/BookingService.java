package com.QuickPool.service;

import com.QuickPool.dtos.BookingRequestDto;
import com.QuickPool.dtos.BookingWithRideDto;
import com.QuickPool.dtos.CreateBookingDto;
import com.QuickPool.dtos.PageResponseDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SafetyService safetyService;

    @Autowired
    private ActivityLogService activityLogService;

    /**
     * Passenger requests a seat. The seat is held immediately (so two passengers can't
     * request the same last seat) but the booking stays PENDING until the driver decides.
     */
    @Transactional
    public UUID bookRide(CreateBookingDto dto, UUID passengerId) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(dto.getRideOfferId())
                .orElseThrow(() -> new NotFoundException("Ride offer not found"));

        if (offer.getDriverId().equals(passengerId)) {
            throw new ForbiddenException("You cannot book your own ride");
        }
        if (safetyService.isHidden(passengerId, offer.getDriverId())) {
            throw new ForbiddenException("This ride is not available to you");
        }
        if (offer.getStatus() != RideStatus.ACTIVE) {
            throw new ConflictException("Ride is not available for booking");
        }
        if (offer.getSeatsAvailable() < dto.getSeatsBooked()) {
            throw new ConflictException("Not enough seats available");
        }
        if (bookingRepository.existsByRideOfferIdAndPassengerIdAndStatusIn(
                offer.getId(), passengerId, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
            throw new ConflictException("You already have a booking on this ride");
        }

        Booking booking = new Booking();
        booking.setRideOfferId(offer.getId());
        booking.setPassengerId(passengerId);
        booking.setSeatsBooked(dto.getSeatsBooked());
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        holdSeats(offer, dto.getSeatsBooked());

        activityLogService.log(passengerId, "BOOKING_REQUESTED", "BOOKING", booking.getId(), null);

        notificationService.notifyUser(offer.getDriverId(), "New booking request",
                passengerLabel(passengerId) + " requested " + dto.getSeatsBooked() + " seat(s) on your ride.",
                NotificationType.BOOKING_REQUESTED, booking.getId());

        return booking.getId();
    }

    /** Driver accepts a pending request. Seats were already held at request time. */
    @Transactional
    public void acceptBooking(UUID bookingId, UUID driverId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        requireDriverOwns(booking, driverId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("This request is no longer pending");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        activityLogService.log(driverId, "BOOKING_ACCEPTED", "BOOKING", bookingId, null);

        notificationService.notifyUser(booking.getPassengerId(), "Booking confirmed",
                "The driver accepted your request. Your seat is confirmed.",
                NotificationType.BOOKING_ACCEPTED, booking.getId());
    }

    /** Driver declines a pending request, releasing the held seats. */
    @Transactional
    public void rejectBooking(UUID bookingId, UUID driverId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        RideOffer offer = requireDriverOwns(booking, driverId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("This request is no longer pending");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        releaseSeats(offer.getId(), booking.getSeatsBooked());

        activityLogService.log(driverId, "BOOKING_REJECTED", "BOOKING", bookingId, null);

        notificationService.notifyUser(booking.getPassengerId(), "Request declined",
                "The driver could not take your booking this time.",
                NotificationType.BOOKING_REJECTED, booking.getId());
    }

    /** Passenger withdraws their own request or confirmed seat. */
    @Transactional
    public void cancelBooking(UUID bookingId, UUID passengerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        if (!booking.getPassengerId().equals(passengerId)) {
            throw new ForbiddenException("Not the owner of this booking");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Booking is not active");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        RideOffer offer = releaseSeats(booking.getRideOfferId(), booking.getSeatsBooked());

        activityLogService.log(passengerId, "BOOKING_CANCELLED", "BOOKING", bookingId, null);

        notificationService.notifyUser(offer.getDriverId(), "Booking cancelled",
                passengerLabel(passengerId) + " cancelled their seat on your ride.",
                NotificationType.BOOKING_CANCELLED, booking.getId());
    }

    /**
     * One page of this passenger's bookings, newest first. Rides for the whole page are
     * fetched in a single {@code findAllById} rather than one {@code findById} per booking —
     * that loop used to run one extra query per row (PRODUCTION_TASKS.md 3.3).
     */
    public PageResponseDto<BookingWithRideDto> getMyBookings(UUID passengerId, Pageable pageable) {
        Slice<Booking> slice = bookingRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId, pageable);
        Map<UUID, RideOffer> offers = ridesByIds(slice.getContent(), Booking::getRideOfferId);

        var content = slice.getContent().stream()
                .map(b -> new BookingWithRideDto(b, offers.get(b.getRideOfferId())))
                .toList();
        return new PageResponseDto<>(content, slice.hasNext());
    }

    /** One page of bookings made on rides this driver owns, newest first. */
    public PageResponseDto<BookingRequestDto> getBookingRequestsForDriver(UUID driverId, Pageable pageable) {
        List<UUID> myRideIds = rideOfferRepository.findByDriverId(driverId).stream()
                .map(RideOffer::getId)
                .collect(Collectors.toList());
        if (myRideIds.isEmpty()) {
            return new PageResponseDto<>(List.of(), false);
        }

        Slice<Booking> slice = bookingRepository.findByRideOfferIdInOrderByCreatedAtDesc(myRideIds, pageable);
        Map<UUID, RideOffer> offers = ridesByIds(slice.getContent(), Booking::getRideOfferId);
        Map<UUID, User> passengers = userRepository.findAllById(
                        slice.getContent().stream().map(Booking::getPassengerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        var content = slice.getContent().stream()
                .map(b -> new BookingRequestDto(b, offers.get(b.getRideOfferId()), passengers.get(b.getPassengerId())))
                .toList();
        return new PageResponseDto<>(content, slice.hasNext());
    }

    private Map<UUID, RideOffer> ridesByIds(List<Booking> bookings, java.util.function.Function<Booking, UUID> rideOfferId) {
        var ids = bookings.stream().map(rideOfferId).distinct().toList();
        return rideOfferRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RideOffer::getId, r -> r));
    }

    private RideOffer requireDriverOwns(Booking booking, UUID driverId) {
        RideOffer offer = rideOfferRepository.findById(booking.getRideOfferId())
                .orElseThrow(() -> new NotFoundException("Ride offer not found"));
        if (!offer.getDriverId().equals(driverId)) {
            throw new ForbiddenException("Not the owner of this ride");
        }
        return offer;
    }

    private void holdSeats(RideOffer offer, short seats) {
        short remaining = (short) (offer.getSeatsAvailable() - seats);
        offer.setSeatsAvailable(remaining);
        if (remaining == 0) {
            offer.setStatus(RideStatus.FULL);
        }
        offer.setUpdatedAt(LocalDateTime.now());
        rideOfferRepository.save(offer);
    }

    private RideOffer releaseSeats(UUID rideOfferId, short seats) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(rideOfferId)
                .orElseThrow(() -> new NotFoundException("Ride offer not found"));
        offer.setSeatsAvailable((short) (offer.getSeatsAvailable() + seats));
        if (offer.getStatus() == RideStatus.FULL) {
            offer.setStatus(RideStatus.ACTIVE);
        }
        offer.setUpdatedAt(LocalDateTime.now());
        return rideOfferRepository.save(offer);
    }

    private String passengerLabel(UUID passengerId) {
        return userRepository.findById(passengerId)
                .map(u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : "A passenger")
                .orElse("A passenger");
    }
}
