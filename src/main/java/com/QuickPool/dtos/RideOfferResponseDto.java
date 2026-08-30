package com.QuickPool.dtos;

import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.entity.Vehicle;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RideOfferResponseDto {
    private final UUID id;
    private final UUID driverId;
    private final Double originLat, originLng, destinationLat, destinationLng;
    private final LocalDateTime departureTime;
    private final Short seatsAvailable;
    private final BigDecimal pricePerSeat;

    // Who you'd be riding with. Deliberately no phone number — that is only
    // shared once a booking is CONFIRMED, via the booking endpoints.
    private final String driverName;
    private final BigDecimal driverRating;
    private final String vehicle;

    public RideOfferResponseDto(RideOffer r) {
        this(r, null, null);
    }

    public RideOfferResponseDto(RideOffer r, User driver, Vehicle vehicle) {
        this.id = r.getId();
        this.driverId = r.getDriverId();
        this.originLat = r.getOriginLat();
        this.originLng = r.getOriginLng();
        this.destinationLat = r.getDestinationLat();
        this.destinationLng = r.getDestinationLng();
        this.departureTime = r.getDepartureTime();
        this.seatsAvailable = r.getSeatsAvailable();
        this.pricePerSeat = r.getPricePerSeat();
        this.driverName = driver == null || driver.getName() == null || driver.getName().isBlank()
                ? "QuickPool driver" : driver.getName();
        this.driverRating = driver == null ? null : driver.getRatingAvg();
        this.vehicle = vehicle == null ? null : new VehicleDto(vehicle).describe();
    }
}
