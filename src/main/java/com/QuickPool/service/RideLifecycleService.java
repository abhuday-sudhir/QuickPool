package com.QuickPool.service;

import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Nothing ever moved rides out of ACTIVE or IN_PROGRESS, so week-old rides stayed
 * bookable and still offered a "Start" button that could only fail. This ages them out.
 */
@Service
@Slf4j
public class RideLifecycleService {

    /** Grace after departure before an unstarted ride is written off. */
    private static final int EXPIRE_AFTER_HOURS = 3;

    /** A city ride left running this long was simply never closed by the driver. */
    private static final int COMPLETE_AFTER_HOURS = 6;

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.lifecycle.interval-ms:900000}")
    @Transactional
    public void sweep() {
        expireUnstarted();
        completeStale();
    }

    private void expireUnstarted() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(EXPIRE_AFTER_HOURS);
        List<RideOffer> stale = rideOfferRepository
                .findByStatusInAndDepartureTimeBefore(List.of(RideStatus.ACTIVE, RideStatus.FULL), cutoff);

        for (RideOffer ride : stale) {
            ride.setStatus(RideStatus.EXPIRED);
            ride.setUpdatedAt(LocalDateTime.now());
            rideOfferRepository.save(ride);

            // Anyone still holding a seat needs to know it is not happening.
            for (Booking b : bookingRepository.findByRideOfferIdAndStatusIn(
                    ride.getId(), List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
                b.setStatus(BookingStatus.CANCELLED);
                b.setUpdatedAt(LocalDateTime.now());
                bookingRepository.save(b);
                notificationService.notifyUser(b.getPassengerId(), "Ride did not run",
                        "The driver never started this ride, so your booking was cancelled.",
                        NotificationType.RIDE_CANCELLED, ride.getId());
            }
        }
        if (!stale.isEmpty()) {
            log.info("Expired {} ride(s) that were never started", stale.size());
        }
    }

    private void completeStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(COMPLETE_AFTER_HOURS);
        List<RideOffer> running = rideOfferRepository
                .findByStatusInAndDepartureTimeBefore(List.of(RideStatus.IN_PROGRESS), cutoff);

        for (RideOffer ride : running) {
            ride.setStatus(RideStatus.COMPLETED);
            ride.setUpdatedAt(LocalDateTime.now());
            rideOfferRepository.save(ride);

            // Completing the bookings is what unlocks rating each other.
            for (Booking b : bookingRepository.findByRideOfferIdAndStatusIn(
                    ride.getId(), List.of(BookingStatus.CONFIRMED))) {
                b.setStatus(BookingStatus.COMPLETED);
                b.setUpdatedAt(LocalDateTime.now());
                bookingRepository.save(b);
            }
        }
        if (!running.isEmpty()) {
            log.info("Completed {} ride(s) that were left running", running.size());
        }
    }
}
