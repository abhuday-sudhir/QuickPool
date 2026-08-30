package com.QuickPool.repository;

import com.QuickPool.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByJti(UUID jti);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true, t.revokedReason = :reason "
            + "where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("reason") String reason);
}
