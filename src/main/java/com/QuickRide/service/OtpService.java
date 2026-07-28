package com.QuickRide.service;

import com.QuickRide.entity.OtpVerification;
import com.QuickRide.entity.User;
import com.QuickRide.repository.OtpVerificationRepository;
import com.QuickRide.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 30;

    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Transactional
    public void requestOtp(String phone) {
        otpRepository.findByPhoneAndVerifiedFalse(phone).ifPresent(existing -> {
            long secondsSinceCreated = java.time.Duration.between(existing.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceCreated < RESEND_COOLDOWN_SECONDS) {
                throw new IllegalStateException("Please wait before requesting another OTP");
            }
            // Expired or cooldown passed — remove the stale row so a new one can be created
            otpRepository.delete(existing);
        });

        String otp = String.format("%06d", random.nextInt(1_000_000));

        OtpVerification verification = new OtpVerification();
        verification.setPhone(phone);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        verification.setAttempts((short) 0);
        verification.setVerified(false);
        verification.setCreatedAt(LocalDateTime.now());
        otpRepository.save(verification);

        // STUB: replace with real SMS provider (Twilio/MSG91) later.
        // Printing to console/logs is ONLY for local development — never do this in production,
        // since server logs are not a secure channel for delivering a login credential.
        log.info("========================================");
        log.info("OTP for {} is: {}", phone, otp);
        log.info("========================================");
    }

    @Transactional
    public UserWithTokens verifyOtp(String phone, String otp) {
        OtpVerification verification = otpRepository.findByPhoneAndVerifiedFalse(phone)
                .orElseThrow(() -> new IllegalArgumentException("No pending OTP for this phone"));

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("OTP has expired, please request a new one");
        }

        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many attempts, please request a new OTP");
        }

        if (!passwordEncoder.matches(otp, verification.getOtpHash())) {
            verification.setAttempts((short) (verification.getAttempts() + 1));
            otpRepository.save(verification);
            throw new IllegalArgumentException("Incorrect OTP");
        }

        verification.setVerified(true);
        otpRepository.save(verification);

        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User newUser = new User();
            newUser.setPhone(phone);
            newUser.setRoleFlags((short) 3); // both rider and driver by default for testing
            newUser.setCreatedAt(LocalDateTime.now());
            return userRepository.save(newUser);
        });

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return new UserWithTokens(user.getId(), accessToken, refreshToken);
    }

    public record UserWithTokens(UUID userId, String accessToken, String refreshToken) {}
}