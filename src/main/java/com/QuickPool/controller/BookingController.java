package com.QuickPool.controller;

import com.QuickPool.dtos.CreateBookingDto;
import com.QuickPool.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping
    public UUID book(@RequestBody CreateBookingDto dto, Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        return bookingService.bookRide(dto, passengerId);
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id, Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        bookingService.cancelBooking(id, passengerId);
    }
}