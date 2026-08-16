package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRideOfferDto {
    private Double originLat;
    private Double originLng;
    private Double destinationLat;
    private Double destinationLng;
    private LocalDateTime departureTime;
    private Short seatsTotal;
    private BigDecimal pricePerSeat;
}
