package com.QuickPool.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SaveAddressDto {

    @NotBlank(message = "Give this address a label, e.g. Home")
    @Size(max = 40, message = "Label is too long")
    private String label;

    @NotBlank(message = "name is required")
    @Size(max = 200, message = "name is too long")
    private String name;

    @NotNull(message = "lat is required")
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double lat;

    @NotNull(message = "lng is required")
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double lng;
}
