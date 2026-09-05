package com.QuickPool.controller;

import com.QuickPool.dtos.BookingRequestDto;
import com.QuickPool.dtos.BookingWithRideDto;
import com.QuickPool.dtos.CreateBookingDto;
import com.QuickPool.dtos.PageResponseDto;
import com.QuickPool.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @PostMapping
    public UUID book(@Valid @RequestBody CreateBookingDto dto, Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        return bookingService.bookRide(dto, passengerId);
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id, Authentication auth) {
        UUID passengerId = (UUID) auth.getPrincipal();
        bookingService.cancelBooking(id, passengerId);
    }

    @PutMapping("/{id}/accept")
    public void accept(@PathVariable UUID id, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        bookingService.acceptBooking(id, driverId);
    }

    @PutMapping("/{id}/reject")
    public void reject(@PathVariable UUID id, Authentication auth) {
        UUID driverId = (UUID) auth.getPrincipal();
        bookingService.rejectBooking(id, driverId);
    }

    @GetMapping("/mine")
    public PageResponseDto<BookingWithRideDto> myBookings(
            Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        UUID passengerId = (UUID) auth.getPrincipal();
        return bookingService.getMyBookings(passengerId, pageable);
    }

    /** Bookings other people made on rides I'm driving. */
    @GetMapping("/requests")
    public PageResponseDto<BookingRequestDto> bookingRequests(
            Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        UUID driverId = (UUID) auth.getPrincipal();
        return bookingService.getBookingRequestsForDriver(driverId, pageable);
    }
}
