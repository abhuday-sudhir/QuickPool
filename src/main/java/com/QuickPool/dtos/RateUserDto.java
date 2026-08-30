package com.QuickPool.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class RateUserDto {

    @NotNull(message = "rideOfferId is required")
    private UUID rideOfferId;

    @NotNull(message = "rateeId is required")
    private UUID rateeId;

    @Min(value = 1, message = "Rating must be 1-5")
    @Max(value = 5, message = "Rating must be 1-5")
    @NotNull(message = "stars is required")
    private Short stars;

    @Size(max = 500, message = "Comment is too long")
    private String comment;
}
