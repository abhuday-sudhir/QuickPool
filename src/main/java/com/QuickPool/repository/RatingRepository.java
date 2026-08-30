package com.QuickPool.repository;

import com.QuickPool.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByRideOfferIdAndRaterIdAndRateeId(UUID rideOfferId, UUID raterId, UUID rateeId);

    List<Rating> findByRateeIdOrderByCreatedAtDesc(UUID rateeId);

    @Query("select avg(r.stars) from Rating r where r.rateeId = :userId")
    Double averageFor(@Param("userId") UUID userId);

    long countByRateeId(UUID rateeId);
}
