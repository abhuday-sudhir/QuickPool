package com.QuickPool.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_reports")
@Getter @Setter
public class UserReport {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_id", nullable = false)
    private UUID reportedId;

    @Column(name = "ride_offer_id")
    private UUID rideOfferId;

    @Column(nullable = false)
    private String reason;

    @Column
    private String details;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
