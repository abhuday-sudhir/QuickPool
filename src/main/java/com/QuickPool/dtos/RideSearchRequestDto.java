package com.QuickPool.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RideSearchRequestDto {
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private LocalDateTime earliestTime;
    private LocalDateTime latestTime;
}