package com.QuickPool.dtos;

import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A booking as the driver sees it, with enough passenger detail to decide on it. */
@Getter
public class BookingRequestDto {
    private final UUID bookingId;
    private final UUID rideOfferId;
    private final UUID passengerId;
    private final String passengerName;
    private final String passengerPhone;
    private final java.math.BigDecimal passengerRating;
    private final Short seatsBooked;
    private final String bookingStatus;
    private final String rideStatus;
    private final LocalDateTime departureTime;
    private final LocalDateTime requestedAt;

    public BookingRequestDto(Booking b, RideOffer r, User passenger) {
        this.bookingId = b.getId();
        this.rideOfferId = r.getId();
        this.passengerId = b.getPassengerId();
        this.passengerName = passenger != null ? passenger.getName() : null;
        // Contact details are withheld until the driver has actually accepted them.
        // A pending request should not leak a stranger's phone number — but once they
        // have ridden together it stays available, including after the ride completes.
        boolean sharedRide = b.getStatus() == BookingStatus.CONFIRMED
                || b.getStatus() == BookingStatus.COMPLETED;
        this.passengerPhone = (passenger != null && sharedRide) ? passenger.getPhone() : null;
        this.passengerRating = passenger != null ? passenger.getRatingAvg() : null;
        this.seatsBooked = b.getSeatsBooked();
        this.bookingStatus = b.getStatus().name();
        this.rideStatus = r.getStatus().name();
        this.departureTime = r.getDepartureTime();
        this.requestedAt = b.getCreatedAt();
    }
}
