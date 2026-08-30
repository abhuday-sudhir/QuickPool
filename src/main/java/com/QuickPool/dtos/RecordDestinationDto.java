package com.QuickPool.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RecordDestinationDto {

    @NotBlank(message = "name is required")
    @Size(max = 200, message = "name is too long")
    private String name;

    @NotNull(message = "lat is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double lat;

    @NotNull(message = "lng is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double lng;
}
