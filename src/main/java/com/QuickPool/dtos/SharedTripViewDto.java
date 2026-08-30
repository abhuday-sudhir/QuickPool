package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What someone following a shared link can see. Deliberately narrow: no phone
 * numbers, no passenger identities, no ride id — just enough to follow along.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SharedTripViewDto {
    private String rideStatus;
    private String driverName;
    private String vehicle;
    private Double originLat;
    private Double originLng;
    private Double destinationLat;
    private Double destinationLng;
    private LocalDateTime departureTime;
    private Double lastLat;
    private Double lastLng;
    private LocalDateTime lastLocationAt;
}
