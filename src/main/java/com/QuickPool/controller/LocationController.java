package com.QuickPool.controller;

import com.QuickPool.dtos.LocationBroadcastDto;
import com.QuickPool.dtos.LocationUpdateDto;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.UUID;

import static com.QuickPool.enums.BookingStatus.CONFIRMED;

@Controller
public class LocationController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RideOfferRepository rideOfferRepository;

    @Autowired
    private BookingRepository bookingRepository;

    // Client sends to: /app/ride/{rideId}/location
    // Server broadcasts to: /topic/ride/{rideId}/location
    @MessageMapping("/ride/{rideId}/location")
    public void updateLocation(@DestinationVariable UUID rideId,
                               LocationUpdateDto update,
                               SimpMessageHeaderAccessor headerAccessor) {

        UUID userId = (UUID) headerAccessor.getSessionAttributes().get("userId");

        RideOffer offer = rideOfferRepository.findById(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));

        String role;
        if (offer.getDriverId().equals(userId)) {
            role = "DRIVER";
        } else {
            boolean isPassenger = !bookingRepository
                    .findByRideOfferIdAndStatus(rideId, CONFIRMED)
                    .stream()
                    .filter(b -> b.getPassengerId().equals(userId))
                    .toList().isEmpty();
            if (!isPassenger) {
                throw new IllegalStateException("Not authorized for this ride's location channel");
            }
            role = "PASSENGER";
        }

        LocationBroadcastDto broadcast = new LocationBroadcastDto(
                userId, role, update.getLat(), update.getLng(), System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/ride/" + rideId + "/location", broadcast);
    }
}