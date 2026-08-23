package com.QuickPool.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateBookingDto {

    @NotNull(message = "rideOfferId is required")
    private UUID rideOfferId;

    @Min(value = 1, message = "seatsBooked must be at least 1")
    private Short seatsBooked = 1;
}