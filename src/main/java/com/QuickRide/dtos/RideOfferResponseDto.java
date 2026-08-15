package com.QuickRide.dtos;

import com.QuickRide.entity.RideOffer;
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

    public RideOfferResponseDto(RideOffer r) {
        this.id = r.getId();
        this.driverId = r.getDriverId();
        this.originLat = r.getOriginLat();
        this.originLng = r.getOriginLng();
        this.destinationLat = r.getDestinationLat();
        this.destinationLng = r.getDestinationLng();
        this.departureTime = r.getDepartureTime();
        this.seatsAvailable = r.getSeatsAvailable();
        this.pricePerSeat = r.getPricePerSeat();
    }
}
