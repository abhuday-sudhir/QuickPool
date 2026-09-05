package com.QuickPool.service;

import com.QuickPool.dtos.AuthResponseDto;
import com.QuickPool.entity.RefreshToken;
import com.QuickPool.entity.User;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.repository.RefreshTokenRepository;
import com.QuickPool.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Refresh tokens are single-use (PRODUCTION_TASKS.md / CLAUDE.md): rotate() spends the
 * presented token and issues a new pair, and a *second* presentation of the same token is
 * treated as theft — burning every session the user has via TokenRevoker, a separate bean
 * so the revoke survives the exception thrown right after it (REQUIRES_NEW).
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private JwtService jwtService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenRevoker tokenRevoker;
    @InjectMocks private TokenService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("issuePair() stores a fresh, unrevoked refresh token record and returns both JWTs")
    void issuePairStoresRecord() {
        when(jwtService.refreshTokenDays()).thenReturn(30L);
        when(jwtService.generateAccessToken(userId)).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken(eq(userId), any())).thenReturn("refresh-jwt");

        AuthResponseDto result = service.issuePair(userId, true);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAccessToken()).isEqualTo("access-jwt");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(result.isProfileComplete()).isTrue();

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getRevoked()).isFalse();
    }

    @Test
    @DisplayName("rotate() rejects a token that doesn't even verify")
    void rotateRejectsInvalidToken() {
        when(jwtService.isValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.rotate("bad")).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("rotate() rejects an access token presented as a refresh token")
    void rotateRejectsWrongType() {
        when(jwtService.isValid("access-jwt")).thenReturn(true);
        when(jwtService.extractType("access-jwt")).thenReturn("access");

        assertThatThrownBy(() -> service.rotate("access-jwt")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rotate() rejects a pre-rotation token with no jti")
    void rotateRejectsMissingJti() {
        when(jwtService.isValid("old")).thenReturn(true);
        when(jwtService.extractType("old")).thenReturn("refresh");
        when(jwtService.extractJti("old")).thenReturn(null);

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rotate() rejects a jti with no matching stored record")
    void rotateRejectsUnknownJti() {
        UUID jti = UUID.randomUUID();
        when(jwtService.isValid("t")).thenReturn(true);
        when(jwtService.extractType("t")).thenReturn("refresh");
        when(jwtService.extractJti("t")).thenReturn(jti);
        when(refreshTokenRepository.findByJti(jti)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rotate() on an already-spent token burns every session and refuses it")
    void rotateDetectsReuse() {
        UUID jti = UUID.randomUUID();
        RefreshToken spent = new RefreshToken();
        spent.setJti(jti);
        spent.setUserId(userId);
        spent.setRevoked(true);
        when(jwtService.isValid("t")).thenReturn(true);
        when(jwtService.extractType("t")).thenReturn("refresh");
        when(jwtService.extractJti("t")).thenReturn(jti);
        when(refreshTokenRepository.findByJti(jti)).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(BadRequestException.class);

        verify(tokenRevoker).revokeAllNow(userId, "REUSE_DETECTED");
        // The theft response must not also issue a fresh pair.
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate() refuses an expired token")
    void rotateRejectsExpired() {
        UUID jti = UUID.randomUUID();
        RefreshToken expired = new RefreshToken();
        expired.setJti(jti);
        expired.setUserId(userId);
        expired.setRevoked(false);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(jwtService.isValid("t")).thenReturn(true);
        when(jwtService.extractType("t")).thenReturn("refresh");
        when(jwtService.extractJti("t")).thenReturn(jti);
        when(refreshTokenRepository.findByJti(jti)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("t")).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(tokenRevoker);
    }

    @Test
    @DisplayName("rotate() happy path: spends the old token and issues a new pair")
    void rotateHappyPath() {
        UUID jti = UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setJti(jti);
        stored.setUserId(userId);
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(10));
        when(jwtService.isValid("old")).thenReturn(true);
        when(jwtService.extractType("old")).thenReturn("refresh");
        when(jwtService.extractJti("old")).thenReturn(jti);
        when(refreshTokenRepository.findByJti(jti)).thenReturn(Optional.of(stored));

        User user = new User();
        user.setId(userId);
        user.setName("Test");
        user.setEmail("t@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.refreshTokenDays()).thenReturn(30L);
        when(jwtService.generateAccessToken(userId)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(eq(userId), any())).thenReturn("new-refresh");

        AuthResponseDto result = service.rotate("old");

        assertThat(stored.getRevoked()).isTrue();
        assertThat(stored.getRevokedReason()).isEqualTo("ROTATED");
        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.isProfileComplete()).isTrue();
        // Once for spending the old record, once for the freshly issued one.
        verify(refreshTokenRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("rotate() refuses a token whose user account was deleted")
    void rotateRejectsDeletedUser() {
        UUID jti = UUID.randomUUID();
        RefreshToken stored = new RefreshToken();
        stored.setJti(jti);
        stored.setUserId(userId);
        stored.setRevoked(false);
        stored.setExpiresAt(LocalDateTime.now().plusDays(10));
        when(jwtService.isValid("old")).thenReturn(true);
        when(jwtService.extractType("old")).thenReturn("refresh");
        when(jwtService.extractJti("old")).thenReturn(jti);
        when(refreshTokenRepository.findByJti(jti)).thenReturn(Optional.of(stored));

        User deletedUser = new User();
        deletedUser.setId(userId);
        deletedUser.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("revokeAll() delegates straight to the repository")
    void revokeAllDelegates() {
        service.revokeAll(userId, "ACCOUNT_DELETED");

        verify(refreshTokenRepository).revokeAllForUser(userId, "ACCOUNT_DELETED");
    }
}
