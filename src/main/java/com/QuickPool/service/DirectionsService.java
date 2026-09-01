package com.QuickPool.service;

import com.QuickPool.config.CacheConfig;
import com.QuickPool.dtos.DirectionsDto;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.exception.NotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Server-side proxy for the Google Directions Web Service.
 *
 * The point is that the key never reaches the handset. Directions is a Web Service API,
 * so Google cannot restrict it by Android package name and signing certificate the way it
 * restricts the Maps SDK — a key shipped in the APK is extractable and billable by anyone
 * who pulls it. Held here instead, it can carry an IP restriction naming this server.
 *
 * Results are cached: the same commute is routed over and over by different users, and the
 * geometry of a road does not change between requests. Every cache hit is a billed call we
 * did not make.
 */
@Service
@Slf4j
public class DirectionsService {

    private static final String ENDPOINT = "https://maps.googleapis.com/maps/api/directions/json";

    private final RestClient restClient = RestClient.create();

    @Value("${app.maps.directions-key:}")
    private String apiKey;

    /**
     * Coordinates are rounded to 4 decimals (~11m) inside the cache key. A GPS fix jitters by
     * a few metres while standing still, and unrounded keys would miss on every one of those;
     * at this precision the route returned is the same road.
     *
     * The rounding lives in the key expression rather than in a helper this method calls,
     * because @Cacheable only applies through the proxy — routing via a second method on this
     * same bean would be a self-invocation and would silently skip the cache entirely.
     */
    @Cacheable(cacheNames = CacheConfig.DIRECTIONS, key =
            "T(java.lang.Math).round(#originLat * 10000) + ',' + T(java.lang.Math).round(#originLng * 10000)"
          + " + '|' + T(java.lang.Math).round(#destLat * 10000) + ',' + T(java.lang.Math).round(#destLng * 10000)")
    public DirectionsDto route(double originLat, double originLng, double destLat, double destLng) {
        requireCoordinate(originLat, -90, 90, "originLat");
        requireCoordinate(originLng, -180, 180, "originLng");
        requireCoordinate(destLat, -90, 90, "destLat");
        requireCoordinate(destLng, -180, 180, "destLng");

        if (apiKey == null || apiKey.isBlank()) {
            // Loud, because the symptom on the app side is just "no route drawn".
            log.error("app.maps.directions-key is not set — set MAPS_SERVER_KEY in the environment");
            throw new IllegalStateException("Directions is not configured on this server");
        }

        String url = ENDPOINT
                + "?origin=" + originLat + "," + originLng
                + "&destination=" + destLat + "," + destLng
                + "&key=" + apiKey;

        GoogleResponse body;
        try {
            body = restClient.get().uri(url).retrieve().body(GoogleResponse.class);
        } catch (Exception e) {
            log.warn("Directions call failed: {}", e.getMessage());
            throw new NotFoundException("Could not reach the routing service");
        }

        if (body == null || !"OK".equals(body.status())) {
            // REQUEST_DENIED / OVER_QUERY_LIMIT are our problem, not the user's — log the
            // detail Google returns, since it is the only thing that explains a dead key.
            if (body != null && !"ZERO_RESULTS".equals(body.status())) {
                log.error("Directions returned {}: {}", body.status(), body.errorMessage());
            }
            throw new NotFoundException("No route found");
        }

        Route route = first(body.routes());
        Leg leg = route == null ? null : first(route.legs());
        if (route == null || leg == null || route.overviewPolyline() == null) {
            throw new NotFoundException("No route found");
        }

        return new DirectionsDto(
                route.overviewPolyline().points(),
                leg.distance().text(),
                leg.duration().text(),
                leg.distance().value(),
                leg.duration().value()
        );
    }

    private static <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    // Only the handful of fields we use. Bound as records rather than a generic tree because
    // Boot 4 ships Jackson 3, where the tree API moved package and renamed accessors — this
    // shape is stable across both.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleResponse(String status,
                          @JsonProperty("error_message") String errorMessage,
                          List<Route> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(@JsonProperty("overview_polyline") Polyline overviewPolyline, List<Leg> legs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Polyline(String points) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Leg(Measure distance, Measure duration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Measure(String text, long value) {}

    private static void requireCoordinate(double value, double min, double max, String name) {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new BadRequestException(name + " is not a valid coordinate");
        }
    }
}
