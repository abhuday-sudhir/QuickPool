package com.QuickPool.service;

import com.QuickPool.entity.EmailVerification;
import com.QuickPool.entity.User;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.EmailVerificationRepository;
import com.QuickPool.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final int EXPIRY_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    private EmailVerificationRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailSender emailSender;

    @Transactional
    public void requestCode(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("Add an email address first");
        }
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ConflictException("Your email is already verified");
        }

        var existing = repository.findByUserIdAndVerifiedFalse(userId);
        if (existing.isPresent()) {
            long age = Duration.between(existing.get().getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (age < RESEND_COOLDOWN_SECONDS) {
                throw new ConflictException("Please wait before requesting another code");
            }
            repository.delete(existing.get());
            // Same Hibernate flush-ordering trap as OTP: the partial unique index on
            // (user_id) WHERE verified = false rejects the insert unless the delete lands first.
            repository.flush();
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        EmailVerification verification = new EmailVerification();
        verification.setUserId(userId);
        verification.setEmail(user.getEmail());
        verification.setCodeHash(encoder.encode(code));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        verification.setAttempts((short) 0);
        verification.setVerified(false);
        verification.setCreatedAt(LocalDateTime.now());
        repository.save(verification);

        emailSender.send(user.getEmail(), "Verify your QuickPool email",
                "Your verification code is " + code + ". It expires in " + EXPIRY_MINUTES + " minutes.");
    }

    @Transactional
    public void confirm(UUID userId, String code) {
        EmailVerification verification = repository.findByUserIdAndVerifiedFalse(userId)
                .orElseThrow(() -> new NotFoundException("Request a verification code first"));

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("That code has expired, request a new one");
        }
        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            throw new ConflictException("Too many attempts, request a new code");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // A changed email invalidates a code issued for the old one.
        if (!verification.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Your email changed, request a new code");
        }
        if (!encoder.matches(code, verification.getCodeHash())) {
            verification.setAttempts((short) (verification.getAttempts() + 1));
            repository.save(verification);
            throw new BadRequestException("Incorrect code");
        }

        verification.setVerified(true);
        repository.save(verification);

        user.setEmailVerified(true);
        userRepository.save(user);
    }
}
