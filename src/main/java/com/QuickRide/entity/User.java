package com.QuickRide.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String phone;

    private String name;
    private String email;

    @Column(name = "role_flags", nullable = false)
    private Short roleFlags = 1;

    @Column(name = "rating_avg")
    private java.math.BigDecimal ratingAvg;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}
