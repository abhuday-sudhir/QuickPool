package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deliberately free of temporal types — this shape is what gets JSON-serialised into
 * Redis, and keeping it primitive avoids Jackson time-module coupling in the cache.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DestinationDto {
    private String name;
    private Double lat;
    private Double lng;
    private Integer useCount;
}
