package com.QuickPool.repository;

import com.QuickPool.entity.TripShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripShareRepository extends JpaRepository<TripShare, UUID> {
    Optional<TripShare> findByToken(String token);
    List<TripShare> findByRideOfferIdAndSharedByAndRevokedFalse(UUID rideOfferId, UUID sharedBy);
}
