package com.QuickPool.service;

import com.QuickPool.dtos.BookingWithRideDto;
import com.QuickPool.dtos.CreateBookingDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ActivityLogService activityLogService;

    @Transactional
    public UUID bookRide(CreateBookingDto dto, UUID passengerId) {
        RideOffer offer = rideOfferRepository.findByIdForUpdate(dto.getRideOfferId())
                .orElseThrow(() -> new IllegalArgumentException("Ride offer not found"));

        if (offer.getStatus() != RideStatus.ACTIVE) {
            throw new ConflictException("Ride is not available for booking");
        }
        if (offer.getSeatsAvailable() < dto.getSeatsBooked()) {
            throw new ConflictException("Not enough seats available");
        }

        Booking booking = new Booking();
        booking.setRideOfferId(offer.getId());
        booking.setPassengerId(passengerId);
        booking.setSeatsBooked(dto.getSeatsBooked());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        short remaining = (short) (offer.getSeatsAvailable() - dto.getSeatsBooked());
        offer.setSeatsAvailable(remaining);
        if (remaining == 0) {
            offer.setStatus(RideStatus.FULL);
        }
        offer.setUpdatedAt(LocalDateTime.now());
        rideOfferRepository.save(offer);

        activityLogService.log(passengerId, "BOOKING_CREATED", "BOOKING", booking.getId(), null);

        notificationService.notifyUser(offer.getDriverId(), "New booking",
                "A passenger booked " + dto.getSeatsBooked() + " seat(s) on your ride.");

        return booking.getId();
    }

    @Transactional
    public void cancelBooking(UUID bookingId, UUID passengerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (!booking.getPassengerId().equals(passengerId)) {
            throw new IllegalStateException("Not the owner of this booking");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking is not active");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        RideOffer offer = rideOfferRepository.findByIdForUpdate(booking.getRideOfferId())
                .orElseThrow(() -> new IllegalArgumentException("Ride offer not found"));

        offer.setSeatsAvailable((short) (offer.getSeatsAvailable() + booking.getSeatsBooked()));
        if (offer.getStatus() == RideStatus.FULL) {
            offer.setStatus(RideStatus.ACTIVE);
        }
        offer.setUpdatedAt(LocalDateTime.now());
        rideOfferRepository.save(offer);

        activityLogService.log(passengerId, "BOOKING_CANCELLED", "BOOKING", bookingId, null);

        notificationService.notifyUser(offer.getDriverId(), "Booking cancelled",
                "A passenger cancelled their seat on your ride.");
    }
    public List<BookingWithRideDto> getMyBookings(UUID passengerId) {
        return bookingRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId).stream()
                .map(b -> {
                    RideOffer offer = rideOfferRepository.findById(b.getRideOfferId())
                            .orElseThrow(() -> new NotFoundException("Ride offer not found"));
                    return new BookingWithRideDto(b, offer);
                })
                .collect(Collectors.toList());
    }
}