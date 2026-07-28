package com.QuickRide.repository;

import com.QuickRide.entity.Booking;
import com.QuickRide.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByRideOfferIdAndStatus(UUID rideOfferId, BookingStatus status);
    List<Booking> findByPassengerId(UUID passengerId);
}