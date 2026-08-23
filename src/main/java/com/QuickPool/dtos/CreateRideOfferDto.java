package com.QuickPool.dtos;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRideOfferDto {

    @NotNull(message = "originLat is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double originLat;

    @NotNull(message = "originLng is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double originLng;

    @NotNull(message = "destinationLat is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double destinationLat;

    @NotNull(message = "destinationLng is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double destinationLng;

    @NotNull(message = "departureTime is required")
    @Future(message = "departureTime must be in the future")
    private LocalDateTime departureTime;

    @NotNull(message = "seatsTotal is required")
    @Min(value = 1, message = "seatsTotal must be at least 1")
    @Max(value = 8, message = "seatsTotal seems unreasonably high")
    private Short seatsTotal;

    @DecimalMin(value = "0.0", message = "pricePerSeat cannot be negative")
    private BigDecimal pricePerSeat;
}
