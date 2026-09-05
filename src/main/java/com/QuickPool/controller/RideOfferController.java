package com.QuickPool.controller;

import com.QuickPool.dtos.*;
import com.QuickPool.service.RideOfferService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    public RideOfferResponseDto create(@Valid  @RequestBody CreateRideOfferDto dto, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        return rideOfferService.createRideOffer(dto, driverId);
    }

    @PostMapping("/search")
    public List<RideOfferResponseDto> search(@RequestBody RideSearchRequestDto dto, Authentication auth) {
        UUID viewerId = (UUID) auth.getPrincipal();
        return rideOfferService.search(dto, viewerId);
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        rideOfferService.cancelRideOffer(id, driverId);
    }
    @PutMapping("/{id}/start")
    public void start(@PathVariable UUID id, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        rideOfferService.startRide(id, driverId);
    }

    /** Batch lookup for rides the caller is involved in. */
    @GetMapping
    public List<RideOfferResponseDto> byIds(@RequestParam("ids") List<UUID> ids, Authentication auth) {
        UUID viewerId = (UUID) auth.getPrincipal();
        return rideOfferService.visibleByIds(ids, viewerId);
    }

    @GetMapping("/mine")
    public PageResponseDto<RideOfferResponseDto> myRides(
            Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        UUID driverId = (UUID) auth.getPrincipal();
        return rideOfferService.getMyRides(driverId, pageable);
    }
}