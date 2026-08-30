package com.QuickPool.dtos;

import com.QuickPool.entity.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleDto {
    private String make;
    private String model;
    private String color;
    private String plate;

    public VehicleDto(Vehicle v) {
        this.make = v.getMake();
        this.model = v.getModel();
        this.color = v.getColor();
        this.plate = v.getPlate();
    }

    /** "White Maruti Swift · DL3CAB1234" — what a passenger looks for at the kerb. */
    public String describe() {
        return color + " " + make + " " + model + " · " + plate;
    }
}
