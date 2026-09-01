package com.QuickPool.controller;

import com.QuickPool.dtos.DirectionsDto;
import com.QuickPool.exception.TooManyRequestsException;
import com.QuickPool.service.DirectionsService;
import com.QuickPool.service.RateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Routing for the app, so the Directions key can stay on the server.
 *
 * Authenticated like everything else, which is half the protection: a scraper now needs a
 * real account before it can cost us anything. The rate limit is the other half.
 */
@RestController
@RequestMapping("/api/v1")
public class DirectionsController {

    @Autowired
    private DirectionsService directionsService;

    @Autowired
    private RateLimiter rateLimiter;

    @GetMapping("/directions")
    public DirectionsDto directions(@RequestParam double originLat,
                                    @RequestParam double originLng,
                                    @RequestParam double destLat,
                                    @RequestParam double destLng,
                                    Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();

        // A live-tracking screen refetches at most every 2 minutes, so an honest session
        // uses well under this. It is a ceiling on one stolen account's damage, not a
        // throttle anyone should meet in normal use.
        if (!rateLimiter.allow("directions", userId.toString(), 100, Duration.ofHours(1))) {
            throw new TooManyRequestsException("Too many route requests. Try again shortly.");
        }

        return directionsService.route(originLat, originLng, destLat, destLng);
    }
}
