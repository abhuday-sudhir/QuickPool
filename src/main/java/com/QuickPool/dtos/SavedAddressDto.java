package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Primitive-only: this shape is JSON-serialised into Redis. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SavedAddressDto {
    private String id;
    private String label;
    private String name;
    private Double lat;
    private Double lng;
}
