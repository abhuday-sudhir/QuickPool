package com.QuickPool.service;

import com.QuickPool.entity.EmailVerification;
import com.QuickPool.entity.User;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.EmailVerificationRepository;
import com.QuickPool.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private EmailSender emailSender;
    @InjectMocks private EmailVerificationService service;

    private final UUID userId = UUID.randomUUID();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private User userWithEmail(String email, boolean verified) {
        User u = new User();
        u.setId(userId);
        u.setEmail(email);
        u.setEmailVerified(verified);
        return u;
    }

    private EmailVerification pending(String email, String code, LocalDateTime createdAt) {
        EmailVerification v = new EmailVerification();
        v.setUserId(userId);
        v.setEmail(email);
        v.setCodeHash(encoder.encode(code));
        v.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        v.setAttempts((short) 0);
        v.setVerified(false);
        v.setCreatedAt(createdAt);
        return v;
    }

    @Test
    @DisplayName("requestCode() refuses a user with no email on file")
    void requestCodeNoEmail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail(null, false)));

        assertThatThrownBy(() -> service.requestCode(userId)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("requestCode() refuses a user whose email is already verified")
    void requestCodeAlreadyVerified() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("a@b.com", true)));

        assertThatThrownBy(() -> service.requestCode(userId)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("requestCode() refuses a resend within the cooldown")
    void requestCodeCooldown() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("a@b.com", false)));
        when(repository.findByUserIdAndVerifiedFalse(userId))
                .thenReturn(Optional.of(pending("a@b.com", "123456", LocalDateTime.now().minusSeconds(5))));

        assertThatThrownBy(() -> service.requestCode(userId)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("requestCode() replaces a stale pending code (delete then flush) and emails a fresh one")
    void requestCodeReplacesStaleAndSends() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("a@b.com", false)));
        EmailVerification stale = pending("a@b.com", "123456", LocalDateTime.now().minusSeconds(120));
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.of(stale));

        service.requestCode(userId);

        verify(repository).delete(stale);
        verify(repository).flush();
        verify(repository).save(any());
        verify(emailSender).send(eq("a@b.com"), any(), any());
    }

    @Test
    @DisplayName("confirm() 404s when no code was ever requested")
    void confirmNoPending() {
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(userId, "123456")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("confirm() rejects an expired code")
    void confirmExpired() {
        EmailVerification v = pending("a@b.com", "123456", LocalDateTime.now());
        v.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.confirm(userId, "123456")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("confirm() locks out after too many wrong attempts")
    void confirmTooManyAttempts() {
        EmailVerification v = pending("a@b.com", "123456", LocalDateTime.now());
        v.setAttempts((short) 5);
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.confirm(userId, "123456")).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("confirm() rejects a code issued for an email the user has since changed")
    void confirmEmailChanged() {
        EmailVerification v = pending("old@b.com", "123456", LocalDateTime.now());
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.of(v));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("new@b.com", false)));

        assertThatThrownBy(() -> service.confirm(userId, "123456")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("confirm() with the wrong code increments attempts and stays unverified")
    void confirmWrongCode() {
        EmailVerification v = pending("a@b.com", "123456", LocalDateTime.now());
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.of(v));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("a@b.com", false)));

        assertThatThrownBy(() -> service.confirm(userId, "000000")).isInstanceOf(BadRequestException.class);

        assertThat(v.getAttempts()).isEqualTo((short) 1);
        assertThat(v.getVerified()).isFalse();
    }

    @Test
    @DisplayName("confirm() with the right code verifies both the code and the user's email")
    void confirmHappyPath() {
        EmailVerification v = pending("a@b.com", "123456", LocalDateTime.now());
        User user = userWithEmail("a@b.com", false);
        when(repository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.of(v));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.confirm(userId, "123456");

        assertThat(v.getVerified()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
        verify(repository).save(v);
        verify(userRepository).save(user);
    }
}
