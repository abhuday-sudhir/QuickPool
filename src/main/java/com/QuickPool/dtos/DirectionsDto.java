package com.QuickPool.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A route as the app needs it. The geometry stays in Google's encoded-polyline form —
 * the client already decodes it, and the encoded string is a fraction of the size of
 * an expanded coordinate list (which matters both on the wire and in the Redis entry).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DirectionsDto {
    private String polyline;
    private String distanceText;
    private String durationText;
    private long distanceMeters;
    private long durationSeconds;
}
