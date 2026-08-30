package com.QuickPool.repository;

import com.QuickPool.entity.Booking;
import com.QuickPool.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByRideOfferIdAndStatus(UUID rideOfferId, BookingStatus status);
    List<Booking> findByRideOfferIdAndStatusIn(UUID rideOfferId, Collection<BookingStatus> statuses);
    List<Booking> findByPassengerId(UUID passengerId);
    List<Booking> findByPassengerIdOrderByCreatedAtDesc(UUID passengerId);
    List<Booking> findByRideOfferIdInOrderByCreatedAtDesc(Collection<UUID> rideOfferIds);
    boolean existsByRideOfferIdAndPassengerIdAndStatusIn(
            UUID rideOfferId, UUID passengerId, Collection<BookingStatus> statuses);
}
