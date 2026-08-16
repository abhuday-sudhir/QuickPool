package com.QuickPool.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "otp_verifications")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OtpVerification {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String phone;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private Short attempts = 0;

    private Boolean verified = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
