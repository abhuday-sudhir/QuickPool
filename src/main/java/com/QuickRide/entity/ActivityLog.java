package com.QuickRide.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activity_logs")
@Data
public class ActivityLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id")
    private UUID userId; // nullable — some actions (e.g. failed OTP) may not have a resolved user

    @Column(nullable = false)
    private String action; // e.g. "OTP_REQUESTED", "RIDE_CREATED", "BOOKING_CANCELLED"

    @Column(name = "entity_type")
    private String entityType; // e.g. "RIDE_OFFER", "BOOKING"

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(columnDefinition = "TEXT")
    private String metadata; // small JSON string, e.g. {"phone":"+91..."}

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}