package com.QuickPool.service;

import com.QuickPool.dtos.AuthResponseDto;
import com.QuickPool.dtos.UserResponseDto;
import com.QuickPool.entity.RefreshToken;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.repository.RefreshTokenRepository;
import com.QuickPool.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Issues and rotates token pairs. Refresh tokens are single-use. */
@Service
@Slf4j
public class TokenService {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenRevoker tokenRevoker;

    @Transactional
    public AuthResponseDto issuePair(UUID userId, boolean profileComplete) {
        UUID jti = UUID.randomUUID();

        RefreshToken record = new RefreshToken();
        record.setJti(jti);
        record.setUserId(userId);
        record.setExpiresAt(LocalDateTime.now().plusDays(jwtService.refreshTokenDays()));
        record.setRevoked(false);
        record.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(record);

        return new AuthResponseDto(
                userId,
                jwtService.generateAccessToken(userId),
                jwtService.generateRefreshToken(userId, jti),
                profileComplete);
    }

    /**
     * Rotate: the presented token is spent and a fresh pair issued. If a token that
     * was already spent comes back, treat it as theft and revoke everything the user has.
     */
    @Transactional
    public AuthResponseDto rotate(String refreshToken) {
        if (!jwtService.isValid(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (!"refresh".equals(jwtService.extractType(refreshToken))) {
            throw new BadRequestException("Not a refresh token");
        }

        UUID jti = jwtService.extractJti(refreshToken);
        if (jti == null) {
            // Issued before rotation existed; make the client sign in again.
            throw new BadRequestException("Please sign in again");
        }

        RefreshToken stored = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new BadRequestException("Please sign in again"));

        if (Boolean.TRUE.equals(stored.getRevoked())) {
            log.warn("Reuse of a spent refresh token for user {} — revoking all sessions", stored.getUserId());
            // Committed independently: the exception below would otherwise roll this back.
            tokenRevoker.revokeAllNow(stored.getUserId(), "REUSE_DETECTED");
            throw new BadRequestException("Please sign in again");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Please sign in again");
        }

        stored.setRevoked(true);
        stored.setRevokedReason("ROTATED");
        refreshTokenRepository.save(stored);

        boolean profileComplete = userRepository.findById(stored.getUserId())
                .filter(u -> u.getDeletedAt() == null)
                .map(UserResponseDto::isComplete)
                .orElseThrow(() -> new BadRequestException("Please sign in again"));

        return issuePair(stored.getUserId(), profileComplete);
    }

    @Transactional
    public void revokeAll(UUID userId, String reason) {
        refreshTokenRepository.revokeAllForUser(userId, reason);
    }
}
