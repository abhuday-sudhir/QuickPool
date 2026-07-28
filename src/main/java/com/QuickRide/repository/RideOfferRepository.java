package com.QuickRide.repository;

import com.QuickRide.entity.RideOffer;
import com.QuickRide.enums.RideStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideOfferRepository extends JpaRepository<RideOffer, UUID> {

    // Row-level lock so two simultaneous bookings can't both grab the last seat
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RideOffer r where r.id = :id")
    Optional<RideOffer> findByIdForUpdate(UUID id);

    List<RideOffer> findByStatusAndDepartureTimeBetween(
            RideStatus status, LocalDateTime from, LocalDateTime to);

    List<RideOffer> findByDriverId(UUID driverId);
}