package com.QuickPool.dtos;

import lombok.Data;

@Data
public class CreateBookingDto {
    private java.util.UUID rideOfferId;
    private Short seatsBooked = 1;
}