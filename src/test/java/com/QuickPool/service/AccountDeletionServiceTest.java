package com.QuickPool.service;

import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.entity.Vehicle;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ride and booking rows deliberately survive account deletion (the other party's history
 * would otherwise develop holes) — only personal data attached to them is scrubbed. These
 * tests cover the two things that actually matter: nobody can delete out from under an
 * upcoming commitment, and the scrub touches every table it's supposed to.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private SavedAddressRepository savedAddressRepository;
    @Mock private UserDestinationRepository userDestinationRepository;
    @Mock private EmergencyContactRepository emergencyContactRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private TokenService tokenService;
    @InjectMocks private AccountDeletionService service;

    private final UUID userId = UUID.randomUUID();

    private User activeUser() {
        User u = new User();
        u.setId(userId);
        u.setPhone("+919990001111");
        u.setName("Someone");
        u.setEmail("someone@example.com");
        return u;
    }

    private void stubEmptyCleanupSources() {
        when(rideOfferRepository.findByDriverId(userId)).thenReturn(List.of());
        when(bookingRepository.findByPassengerId(userId)).thenReturn(List.of());
        when(vehicleRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(emergencyContactRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByUserIdAndVerifiedFalse(userId)).thenReturn(Optional.empty());
        when(savedAddressRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(userDestinationRepository.findByUserIdOrderByUseCountDescLastUsedAtDesc(eq(userId), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("404s for a user id that doesn't exist")
    void missingUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOwnAccount(userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("refuses to delete an already-deleted account")
    void alreadyDeleted() {
        User user = activeUser();
        user.setDeletedAt(java.time.LocalDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deleteOwnAccount(userId)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("refuses while the user is actively driving a ride")
    void refusesWhileDriving() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        RideOffer inProgress = new RideOffer();
        inProgress.setStatus(RideStatus.IN_PROGRESS);
        when(rideOfferRepository.findByDriverId(userId)).thenReturn(List.of(inProgress));

        assertThatThrownBy(() -> service.deleteOwnAccount(userId)).isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("refuses while the user holds a pending or confirmed booking")
    void refusesWhileRiding() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        when(rideOfferRepository.findByDriverId(userId)).thenReturn(List.of());
        Booking confirmed = new Booking();
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByPassengerId(userId)).thenReturn(List.of(confirmed));

        assertThatThrownBy(() -> service.deleteOwnAccount(userId)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("scrubs identity, revokes every session, and cleans up personal rows")
    void happyPathScrubsAndRevokes() {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        stubEmptyCleanupSources();
        Vehicle vehicle = new Vehicle();
        when(vehicleRepository.findByUserId(userId)).thenReturn(Optional.of(vehicle));

        service.deleteOwnAccount(userId);

        assertThat(user.getPhone()).startsWith("del_");
        assertThat(user.getName()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(user.getRatingAvg()).isNull();
        assertThat(user.getDeletedAt()).isNotNull();

        verify(vehicleRepository).delete(vehicle);
        verify(tokenService).revokeAll(userId, "ACCOUNT_DELETED");
        verify(userRepository).save(user);
    }
}
