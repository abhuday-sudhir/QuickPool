package com.QuickPool.dtos;

import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class BookingWithRideDto {
    private final UUID bookingId;
    private final UUID rideOfferId;
    private final String bookingStatus;
    private final String rideStatus;
    private final LocalDateTime departureTime;

    public BookingWithRideDto(Booking b, RideOffer r) {
        this.bookingId = b.getId();
        this.rideOfferId = r.getId();
        this.bookingStatus = b.getStatus().name();
        this.rideStatus = r.getStatus().name();
        this.departureTime = r.getDepartureTime();
    }
}