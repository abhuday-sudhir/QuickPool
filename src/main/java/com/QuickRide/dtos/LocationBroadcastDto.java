package com.QuickRide.dtos;

import lombok.Data;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@AllArgsConstructor
public class LocationBroadcastDto {
    private UUID userId;
    private String role; // "DRIVER" or "PASSENGER"
    private Double lat;
    private Double lng;
    private long timestamp;
}