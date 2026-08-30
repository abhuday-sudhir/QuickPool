package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImpactDto {
    /** Seats shared (as driver or passenger) that actually went ahead. */
    private int sharedRides;
    private double sharedKm;
    private double co2SavedKg;
    /** Whole trees whose yearly absorption matches the CO2 saved. */
    private double treesEquivalent;
}
