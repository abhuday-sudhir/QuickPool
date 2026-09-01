package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One rating the caller has already submitted, so the app can stop offering
 * "Rate" for a person it would only be able to rate once.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GivenRatingDto {
    private String rideOfferId;
    private String rateeId;
    private int stars;
}
