package com.QuickPool.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ratings")
@Getter @Setter
public class Rating {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ride_offer_id", nullable = false)
    private UUID rideOfferId;

    @Column(name = "rater_id", nullable = false)
    private UUID raterId;

    @Column(name = "ratee_id", nullable = false)
    private UUID rateeId;

    @Column(nullable = false)
    private Short stars;

    @Column
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
