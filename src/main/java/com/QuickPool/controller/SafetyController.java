package com.QuickPool.controller;

import com.QuickPool.dtos.RateUserDto;
import com.QuickPool.dtos.RatingDto;
import com.QuickPool.dtos.ReportUserDto;
import com.QuickPool.service.RatingService;
import com.QuickPool.service.SafetyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SafetyController {

    @Autowired
    private SafetyService safetyService;

    @Autowired
    private RatingService ratingService;

    @PostMapping("/ratings")
    public void rate(@Valid @RequestBody RateUserDto dto, Authentication auth) {
        ratingService.rate((UUID) auth.getPrincipal(), dto);
    }

    @GetMapping("/users/{id}/ratings")
    public List<RatingDto> ratingsFor(@PathVariable UUID id) {
        return ratingService.reviewsFor(id);
    }

    @PostMapping("/users/{id}/block")
    public void block(@PathVariable UUID id, Authentication auth) {
        safetyService.block((UUID) auth.getPrincipal(), id);
    }

    @DeleteMapping("/users/{id}/block")
    public void unblock(@PathVariable UUID id, Authentication auth) {
        safetyService.unblock((UUID) auth.getPrincipal(), id);
    }

    @GetMapping("/users/me/blocked")
    public List<UUID> blocked(Authentication auth) {
        return safetyService.blockedIds((UUID) auth.getPrincipal());
    }

    @PostMapping("/reports")
    public void report(@Valid @RequestBody ReportUserDto dto, Authentication auth) {
        safetyService.report((UUID) auth.getPrincipal(), dto);
    }
}
