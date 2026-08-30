package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveVehicleDto {

    @NotBlank(message = "Please enter the make, e.g. Maruti")
    @Size(max = 40)
    private String make;

    @NotBlank(message = "Please enter the model, e.g. Swift")
    @Size(max = 40)
    private String model;

    @NotBlank(message = "Please enter the colour")
    @Size(max = 24)
    private String color;

    @NotBlank(message = "Please enter the number plate")
    @Pattern(regexp = "^[A-Za-z0-9 -]{4,16}$", message = "That doesn't look like a number plate")
    private String plate;
}
