package com.QuickPool.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ReportUserDto {

    @NotNull(message = "reportedId is required")
    private UUID reportedId;

    private UUID rideOfferId;

    @NotBlank(message = "Please choose a reason")
    @Size(max = 40)
    private String reason;

    @Size(max = 1000)
    private String details;
}
