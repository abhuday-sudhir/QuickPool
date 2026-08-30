package com.QuickPool.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    /** Matches the "jti" claim inside the issued refresh JWT. */
    @Column(nullable = false, unique = true)
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean revoked = false;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
