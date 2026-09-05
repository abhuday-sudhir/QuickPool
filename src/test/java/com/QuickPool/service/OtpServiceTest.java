package com.QuickPool.service;

import com.QuickPool.entity.OtpVerification;
import com.QuickPool.entity.User;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.OtpVerificationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phone + OTP is the only login this app has, so every branch here gates who can get in.
 * OtpService builds its own BCryptPasswordEncoder internally (not injected), so "correct OTP"
 * tests hash a known code with a real encoder in setUp rather than mocking password matching.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private OtpVerificationRepository otpRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLogService;
    @InjectMocks private OtpService service;

    private static final String PHONE = "+919990001111";
    private static final String CODE = "123456";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private OtpVerification pending(String hashedCode, LocalDateTime expiresAt, int attempts) {
        OtpVerification v = new OtpVerification();
        v.setPhone(PHONE);
        v.setOtpHash(encoder.encode(hashedCode));
        v.setExpiresAt(expiresAt);
        v.setAttempts((short) attempts);
        v.setVerified(false);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    @Test
    @DisplayName("requestOtp() saves a fresh code when none is pending")
    void requestOtpFreshCode() {
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.empty());

        service.requestOtp(PHONE);

        verify(otpRepository).save(any());
        verify(otpRepository, never()).delete(any());
        verify(activityLogService).log(isNull(), eq("OTP_REQUESTED"), eq("USER"), isNull(), any());
    }

    @Test
    @DisplayName("requestOtp() refuses a resend within the cooldown")
    void requestOtpCooldown() {
        OtpVerification existing = pending(CODE, LocalDateTime.now().plusMinutes(5), 0);
        existing.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requestOtp(PHONE)).isInstanceOf(ConflictException.class);

        verify(otpRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestOtp() deletes and flushes a stale pending code before saving a new one")
    void requestOtpReplacesStale() {
        OtpVerification existing = pending(CODE, LocalDateTime.now().plusMinutes(5), 0);
        existing.setCreatedAt(LocalDateTime.now().minusSeconds(60));
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(existing));

        service.requestOtp(PHONE);

        verify(otpRepository).delete(existing);
        verify(otpRepository).flush();
        verify(otpRepository).save(any());
    }

    @Test
    @DisplayName("verifyOtp() 404s when nothing is pending for this phone")
    void verifyNoPending() {
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(PHONE, CODE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("verifyOtp() rejects an expired code")
    void verifyExpired() {
        OtpVerification v = pending(CODE, LocalDateTime.now().minusMinutes(1), 0);
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.verifyOtp(PHONE, CODE)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("verifyOtp() locks out after too many wrong attempts")
    void verifyTooManyAttempts() {
        OtpVerification v = pending(CODE, LocalDateTime.now().plusMinutes(5), 5);
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.verifyOtp(PHONE, CODE)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("verifyOtp() with the wrong code increments attempts and logs the failure, without verifying")
    void verifyWrongCode() {
        OtpVerification v = pending(CODE, LocalDateTime.now().plusMinutes(5), 1);
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "000000")).isInstanceOf(BadRequestException.class);

        assertThat(v.getAttempts()).isEqualTo((short) 2);
        assertThat(v.getVerified()).isFalse();
        verify(otpRepository).save(v);
        verify(activityLogService).log(isNull(), eq("OTP_VERIFY_FAILED"), eq("USER"), isNull(), any());
    }

    @Test
    @DisplayName("verifyOtp() refuses a code for a deleted account")
    void verifyDeletedAccount() {
        OtpVerification v = pending(CODE, LocalDateTime.now().plusMinutes(5), 0);
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(v));
        User deleted = new User();
        deleted.setPhone(PHONE);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.verifyOtp(PHONE, CODE)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("verifyOtp() creates a new user (both roles by default) on first login")
    void verifyCreatesNewUser() {
        OtpVerification v = pending(CODE, LocalDateTime.now().plusMinutes(5), 0);
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(v));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(java.util.UUID.randomUUID());
            return u;
        });

        OtpService.VerifiedUser result = service.verifyOtp(PHONE, CODE);

        assertThat(result.profileComplete()).isFalse();
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getRoleFlags()).isEqualTo((short) 3);
        verify(activityLogService).log(eq(result.userId()), eq("USER_REGISTERED"), eq("USER"), eq(result.userId()), isNull());
    }

    @Test
    @DisplayName("verifyOtp() logs an existing user in without creating a duplicate row")
    void verifyExistingUserLogsIn() {
        OtpVerification v = pending(CODE, LocalDateTime.now().plusMinutes(5), 0);
        when(otpRepository.findByPhoneAndVerifiedFalse(PHONE)).thenReturn(Optional.of(v));
        User existing = new User();
        existing.setId(java.util.UUID.randomUUID());
        existing.setPhone(PHONE);
        existing.setName("Someone");
        existing.setEmail("someone@example.com");
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(existing));

        OtpService.VerifiedUser result = service.verifyOtp(PHONE, CODE);

        assertThat(result.userId()).isEqualTo(existing.getId());
        assertThat(result.profileComplete()).isTrue();
        verify(userRepository, never()).save(any());
        verify(activityLogService).log(existing.getId(), "USER_LOGIN", "USER", existing.getId(), null);
    }
}
