package com.QuickPool.service;

import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Play requires an in-app way to delete an account.
 *
 * Ride and booking rows survive, because the other party's history would otherwise
 * develop holes — but every piece of personal data attached to them is scrubbed and
 * the account can never be logged into again.
 */
@Service
public class AccountDeletionService {

    @Autowired private UserRepository userRepository;
    @Autowired private RideOfferRepository rideOfferRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private SavedAddressRepository savedAddressRepository;
    @Autowired private UserDestinationRepository userDestinationRepository;
    @Autowired private EmergencyContactRepository emergencyContactRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private TokenService tokenService;

    @Transactional
    public void deleteOwnAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getDeletedAt() != null) {
            throw new ConflictException("This account is already deleted");
        }

        // Refuse while the user is mid-commitment, so nobody is stranded.
        boolean drivingNow = rideOfferRepository.findByDriverId(userId).stream()
                .anyMatch(r -> r.getStatus() == RideStatus.ACTIVE
                        || r.getStatus() == RideStatus.FULL
                        || r.getStatus() == RideStatus.IN_PROGRESS);
        boolean ridingNow = bookingRepository.findByPassengerId(userId).stream()
                .anyMatch(b -> b.getStatus() == BookingStatus.PENDING
                        || b.getStatus() == BookingStatus.CONFIRMED);
        if (drivingNow || ridingNow) {
            throw new ConflictException(
                    "Finish or cancel your upcoming rides before deleting your account");
        }

        // Everything that is purely this person's.
        vehicleRepository.findByUserId(userId).ifPresent(vehicleRepository::delete);
        emergencyContactRepository.findByUserId(userId).ifPresent(emergencyContactRepository::delete);
        emailVerificationRepository.findByUserIdAndVerifiedFalse(userId)
                .ifPresent(emailVerificationRepository::delete);
        savedAddressRepository.deleteAll(savedAddressRepository.findByUserIdOrderByCreatedAtAsc(userId));
        notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
        userDestinationRepository.deleteAll(
                userDestinationRepository.findByUserIdOrderByUseCountDescLastUsedAtDesc(
                        userId, org.springframework.data.domain.Pageable.unpaged()));

        tokenService.revokeAll(userId, "ACCOUNT_DELETED");

        // Scrub identity. Phone stays non-null and unique, so it becomes a placeholder
        // rather than being cleared — and the real number is freed for re-registration.
        // The column is VARCHAR(20), so the placeholder is sized to fit exactly.
        user.setPhone("del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        user.setName(null);
        user.setEmail(null);
        user.setEmailVerified(false);
        user.setRatingAvg(null);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
