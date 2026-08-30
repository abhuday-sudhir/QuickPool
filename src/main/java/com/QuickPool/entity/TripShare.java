package com.QuickPool.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_shares")
@Getter @Setter
public class TripShare {

    @Id
    @GeneratedValue
    private UUID id;

    /** Random, unguessable; this is the whole credential for the public link. */
    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "ride_offer_id", nullable = false)
    private UUID rideOfferId;

    @Column(name = "shared_by", nullable = false)
    private UUID sharedBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean revoked = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
