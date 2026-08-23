package com.QuickPool.controller;

import com.QuickPool.dtos.BookingWithRideDto;
import com.QuickPool.dtos.CreateBookingDto;
import com.QuickPool.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping
    public UUID book(@Valid  @RequestBody CreateBookingDto dto, Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        return bookingService.bookRide(dto, passengerId);
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id, Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        bookingService.cancelBooking(id, passengerId);
    }
    @GetMapping("/mine")
    public List<BookingWithRideDto> myBookings(Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        return bookingService.getMyBookings(passengerId);
    }
}