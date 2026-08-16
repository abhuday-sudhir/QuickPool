package com.QuickPool.service;

import com.QuickPool.entity.OtpVerification;
import com.QuickPool.entity.User;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.OtpVerificationRepository;
import com.QuickPool.repository.UserRepository;
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

    @Autowired
    private ActivityLogService activityLogService;

    @Transactional
    public void requestOtp(String phone) {
        otpRepository.findByPhoneAndVerifiedFalse(phone).ifPresent(existing -> {
            long secondsSinceCreated = java.time.Duration.between(existing.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceCreated < RESEND_COOLDOWN_SECONDS) {
                throw new ConflictException("Please wait before requesting another OTP");
            }
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

        activityLogService.log(null, "OTP_REQUESTED", "USER", null, "{\"phone\":\"" + phone + "\"}");

        // STUB: replace with real SMS provider (Twilio/MSG91) later.
        // Printing to console/logs is ONLY for local development — never do this in production.
        log.info("========================================");
        log.info("OTP for {} is: {}", phone, otp);
        log.info("========================================");
    }

    @Transactional
    public UserWithTokens verifyOtp(String phone, String otp) {
        OtpVerification verification = otpRepository.findByPhoneAndVerifiedFalse(phone)
                .orElseThrow(() -> new NotFoundException("No pending OTP for this phone"));

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP has expired, please request a new one");
        }

        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            throw new ConflictException("Too many attempts, please request a new OTP");
        }

        if (!passwordEncoder.matches(otp, verification.getOtpHash())) {
            verification.setAttempts((short) (verification.getAttempts() + 1));
            otpRepository.save(verification);
            activityLogService.log(null, "OTP_VERIFY_FAILED", "USER", null, "{\"phone\":\"" + phone + "\"}");
            throw new BadRequestException("Incorrect OTP");
        }

        verification.setVerified(true);
        otpRepository.save(verification);

        boolean isNewUser = userRepository.findByPhone(phone).isEmpty();

        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User newUser = new User();
            newUser.setPhone(phone);
            newUser.setRoleFlags((short) 3); // both rider and driver by default for testing
            newUser.setCreatedAt(LocalDateTime.now());
            return userRepository.save(newUser);
        });

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        activityLogService.log(user.getId(), isNewUser ? "USER_REGISTERED" : "USER_LOGIN", "USER", user.getId(), null);

        return new UserWithTokens(user.getId(), accessToken, refreshToken);
    }

    public record UserWithTokens(UUID userId, String accessToken, String refreshToken) {}
}