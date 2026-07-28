package com.QuickRide.entity;

import com.QuickRide.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter @Setter
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ride_offer_id", nullable = false)
    private UUID rideOfferId;

    @Column(name = "passenger_id", nullable = false)
    private UUID passengerId;

    @Column(name = "seats_booked", nullable = false)
    private Short seatsBooked = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}