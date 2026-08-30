package com.QuickPool.controller;

import com.QuickPool.dtos.SharedTripViewDto;
import com.QuickPool.dtos.TripShareDto;
import com.QuickPool.service.TripShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TripShareController {

    @Autowired
    private TripShareService tripShareService;

    @PostMapping("/rides/{rideId}/share")
    public TripShareDto share(@PathVariable UUID rideId, Authentication auth) {
        return tripShareService.share(rideId, (UUID) auth.getPrincipal());
    }

    @DeleteMapping("/rides/{rideId}/share")
    public void revoke(@PathVariable UUID rideId, Authentication auth) {
        tripShareService.revoke(rideId, (UUID) auth.getPrincipal());
    }

    /** Public on purpose: the recipient is not a QuickPool user. */
    @GetMapping("/share/{token}")
    public SharedTripViewDto view(@PathVariable String token) {
        return tripShareService.view(token);
    }
}
