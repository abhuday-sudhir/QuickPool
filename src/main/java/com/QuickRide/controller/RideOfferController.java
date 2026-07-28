package com.QuickRide.controller;

import com.QuickRide.dtos.*;
import com.QuickRide.service.RideOfferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ride-offers")
public class RideOfferController {

    @Autowired
    private RideOfferService rideOfferService;

    @PostMapping
    public RideOfferResponseDto create(@RequestBody CreateRideOfferDto dto, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        return rideOfferService.createRideOffer(dto, driverId);
    }

    @PostMapping("/search")
    public List<RideOfferResponseDto> search(@RequestBody RideSearchRequestDto dto) {
        return rideOfferService.search(dto);
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        rideOfferService.cancelRideOffer(id, driverId);
    }
}