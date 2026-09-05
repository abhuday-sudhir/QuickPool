package com.QuickPool.repository;

import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.RideStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<RideOffer> findByStatusInAndDepartureTimeBefore(
            java.util.Collection<RideStatus> statuses, LocalDateTime cutoff);

    List<RideOffer> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    /**
     * Corridor search, pushed into PostGIS instead of scanning every active ride into Java.
     * {@code route} is a {@code geography(LineString,4326)} column (V10 migration) kept in sync
     * by a DB trigger, with a GiST index — {@code ST_DWithin} on a geography column uses that
     * index's bounding boxes to skip most rows before it ever computes an exact distance, unlike
     * evaluating {@code GeoUtils.distancePointToSegmentMeters} against every matching row in Java.
     * Column list is explicit (not {@code SELECT *}) so it lines up with {@link RideOffer}'s
     * mapped columns — {@code route} itself is deliberately not one of them.
     */
    @Query(value = """
            SELECT id, driver_id, origin_lat, origin_lng, destination_lat, destination_lng,
                   departure_time, seats_total, seats_available, price_per_seat, status,
                   last_lat, last_lng, last_location_at, created_at, updated_at
            FROM ride_offers
            WHERE status = :status
              AND departure_time BETWEEN :from AND :to
              AND driver_id <> :viewerId
              AND seats_available > 0
              AND ST_DWithin(route, ST_SetSRID(ST_MakePoint(:pickupLng, :pickupLat), 4326)::geography, :radiusMeters)
              AND ST_DWithin(route, ST_SetSRID(ST_MakePoint(:dropLng, :dropLat), 4326)::geography, :radiusMeters)
            """, nativeQuery = true)
    List<RideOffer> searchCorridor(
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("viewerId") UUID viewerId,
            @Param("pickupLat") double pickupLat,
            @Param("pickupLng") double pickupLng,
            @Param("dropLat") double dropLat,
            @Param("dropLng") double dropLng,
            @Param("radiusMeters") double radiusMeters);
}
