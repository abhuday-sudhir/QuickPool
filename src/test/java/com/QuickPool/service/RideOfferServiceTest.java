package com.QuickPool.service;

import com.QuickPool.dtos.CreateRideOfferDto;
import com.QuickPool.dtos.PageResponseDto;
import com.QuickPool.dtos.RideOfferResponseDto;
import com.QuickPool.dtos.RideSearchRequestDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.UserRepository;
import com.QuickPool.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PRODUCTION_TASKS.md 3.1 (PostGIS search) and part of 3.3 (N+1 in ride/passenger lookups).
 *
 * The actual spatial predicate — whether ST_DWithin against ride_offers.route really excludes
 * off-corridor rides — is not something Mockito can verify; it lives entirely in the native
 * query the DB executes. That was checked by hand against a real PostGIS-backed database
 * (on-corridor pickup/drop matched, a pickup/drop 1000+ km away did not, and EXPLAIN ANALYZE
 * confirmed idx_ride_offers_route gets used once there's enough data for the planner to prefer
 * it over the status/time index) rather than in an automated test — the same reasoning
 * DirectionsServiceTest gives for not mocking the real Google HTTP call.
 */
@ExtendWith(MockitoExtension.class)
class RideOfferServiceTest {

    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private SafetyService safetyService;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    @InjectMocks private RideOfferService service;

    private final UUID viewer = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID blockedDriver = UUID.randomUUID();

    private RideOffer offerBy(UUID driverId) {
        RideOffer r = new RideOffer();
        r.setId(UUID.randomUUID());
        r.setDriverId(driverId);
        r.setOriginLat(27.17); r.setOriginLng(78.00);
        r.setDestinationLat(27.50); r.setDestinationLng(78.30);
        r.setDepartureTime(LocalDateTime.now().plusHours(1));
        r.setSeatsTotal((short) 3);
        r.setSeatsAvailable((short) 3);
        r.setStatus(RideStatus.ACTIVE);
        return r;
    }

    private RideSearchRequestDto searchDto() {
        RideSearchRequestDto dto = new RideSearchRequestDto();
        dto.setPickupLat(27.20); dto.setPickupLng(78.05);
        dto.setDropLat(27.45); dto.setDropLng(78.25);
        return dto;
    }

    @Test
    @DisplayName("search() pushes the corridor check to PostGIS and only filters blocked drivers in Java")
    void filtersBlockedDriversAfterSpatialSearch() {
        RideOffer visible = offerBy(driver);
        RideOffer blocked = offerBy(blockedDriver);
        when(rideOfferRepository.searchCorridor(
                any(), any(), eq(viewer),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(2000.0)))
                .thenReturn(List.of(visible, blocked));
        when(safetyService.hiddenFrom(viewer)).thenReturn(Set.of(blockedDriver));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(vehicleRepository.findByUserIdIn(any())).thenReturn(List.of());

        List<RideOfferResponseDto> result = service.search(searchDto(), viewer);

        assertThat(result).singleElement()
                .satisfies(dto -> assertThat(dto.getDriverId()).isEqualTo(driver));
    }

    @Test
    @DisplayName("search() defaults to a 3-hour window from now when none is given")
    void defaultsSearchWindow() {
        when(rideOfferRepository.searchCorridor(any(), any(), any(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(safetyService.hiddenFrom(viewer)).thenReturn(Set.of());

        service.search(searchDto(), viewer);

        var fromCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        var toCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(rideOfferRepository).searchCorridor(fromCaptor.capture(), toCaptor.capture(),
                any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());

        assertThat(Duration.between(fromCaptor.getValue(), toCaptor.getValue())).isEqualTo(Duration.ofHours(3));
    }

    @Test
    @DisplayName("search() looks up every match's driver and vehicle in one batched call each, not per ride")
    void batchesDriverAndVehicleLookups() {
        when(rideOfferRepository.searchCorridor(any(), any(), any(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(offerBy(driver), offerBy(driver)));
        when(safetyService.hiddenFrom(viewer)).thenReturn(Set.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(vehicleRepository.findByUserIdIn(any())).thenReturn(List.of());

        service.search(searchDto(), viewer);

        verify(userRepository, times(1)).findAllById(any());
        verify(vehicleRepository, times(1)).findByUserIdIn(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getConfirmedPassengerOrder() returns confirmed passengers in booking order for the driver")
    void passengerOrderForDriver() {
        RideOffer offer = offerBy(driver);
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        when(rideOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(bookingRepository.findByRideOfferIdAndStatusOrderByCreatedAtAsc(offer.getId(), BookingStatus.CONFIRMED))
                .thenReturn(List.of(booking(p1), booking(p2)));

        List<UUID> order = service.getConfirmedPassengerOrder(offer.getId(), driver);

        assertThat(order).containsExactly(p1, p2);
    }

    @Test
    @DisplayName("getConfirmedPassengerOrder() rejects someone not on the ride")
    void passengerOrderRejectsOutsider() {
        RideOffer offer = offerBy(driver);
        UUID outsider = UUID.randomUUID();
        when(rideOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(bookingRepository.findByRideOfferIdAndStatusOrderByCreatedAtAsc(offer.getId(), BookingStatus.CONFIRMED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getConfirmedPassengerOrder(offer.getId(), outsider))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getConfirmedPassengerOrder() 404s for a ride that doesn't exist")
    void passengerOrderMissingRide() {
        UUID rideId = UUID.randomUUID();
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfirmedPassengerOrder(rideId, driver))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getMyRides() maps a Slice straight through, carrying hasNext along")
    void myRidesCarriesHasNext() {
        RideOffer r = offerBy(driver);
        var slice = new SliceImpl<>(List.of(r), PageRequest.of(0, 1), true);
        when(rideOfferRepository.findByDriverIdOrderByCreatedAtDesc(eq(driver), any())).thenReturn(slice);

        PageResponseDto<RideOfferResponseDto> page = service.getMyRides(driver, PageRequest.of(0, 1));

        assertThat(page.isHasNext()).isTrue();
        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.getId()).isEqualTo(r.getId()));
    }

    private Booking booking(UUID passengerId) {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setPassengerId(passengerId);
        b.setStatus(BookingStatus.CONFIRMED);
        return b;
    }

    private CreateRideOfferDto createDto(LocalDateTime departure, Short seats) {
        CreateRideOfferDto dto = new CreateRideOfferDto();
        dto.setOriginLat(27.17); dto.setOriginLng(78.00);
        dto.setDestinationLat(27.50); dto.setDestinationLng(78.30);
        dto.setDepartureTime(departure);
        dto.setSeatsTotal(seats);
        return dto;
    }

    // --- createRideOffer() ------------------------------------------------------------

    @Test
    @DisplayName("createRideOffer() refuses a departure time in the past")
    void createRefusesPastDeparture() {
        var dto = createDto(LocalDateTime.now().minusMinutes(1), (short) 2);

        assertThatThrownBy(() -> service.createRideOffer(dto, driver)).isInstanceOf(ConflictException.class);

        verify(rideOfferRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRideOffer() refuses a ride with no seats")
    void createRefusesNoSeats() {
        var dto = createDto(LocalDateTime.now().plusHours(1), (short) 0);

        assertThatThrownBy(() -> service.createRideOffer(dto, driver)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("createRideOffer() starts ACTIVE with every seat available")
    void createHappyPath() {
        var dto = createDto(LocalDateTime.now().plusHours(1), (short) 3);
        when(rideOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RideOfferResponseDto result = service.createRideOffer(dto, driver);

        assertThat(result.getSeatsAvailable()).isEqualTo((short) 3);
        var saved = org.mockito.ArgumentCaptor.forClass(RideOffer.class);
        verify(rideOfferRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RideStatus.ACTIVE);
        assertThat(saved.getValue().getDriverId()).isEqualTo(driver);
    }

    // --- cancelRideOffer() --------------------------------------------------------------

    @Test
    @DisplayName("cancelRideOffer() refuses someone who doesn't drive this ride")
    void cancelRefusesNonOwner() {
        RideOffer offer = offerBy(driver);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.cancelRideOffer(offer.getId(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancelRideOffer() cancels both confirmed and pending bookings, notifying each passenger")
    void cancelNotifiesEveryAffectedPassenger() {
        RideOffer offer = offerBy(driver);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        Booking confirmed = booking(UUID.randomUUID());
        Booking pending = booking(UUID.randomUUID());
        pending.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findByRideOfferIdAndStatusIn(eq(offer.getId()), any()))
                .thenReturn(List.of(confirmed, pending));

        service.cancelRideOffer(offer.getId(), driver);

        assertThat(offer.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(notificationService).notifyUser(eq(confirmed.getPassengerId()), any(), any(),
                eq(NotificationType.RIDE_CANCELLED), eq(offer.getId()));
        verify(notificationService).notifyUser(eq(pending.getPassengerId()), any(), any(),
                eq(NotificationType.RIDE_CANCELLED), eq(offer.getId()));
    }

    // --- startRide() --------------------------------------------------------------------

    @Test
    @DisplayName("startRide() refuses someone who doesn't drive this ride")
    void startRefusesNonOwner() {
        RideOffer offer = offerBy(driver);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.startRide(offer.getId(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("startRide() refuses a ride that isn't ACTIVE or FULL")
    void startRefusesWrongStatus() {
        RideOffer offer = offerBy(driver);
        offer.setStatus(RideStatus.CANCELLED);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.startRide(offer.getId(), driver)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("startRide() moves to IN_PROGRESS and tells every confirmed passenger")
    void startHappyPath() {
        RideOffer offer = offerBy(driver);
        when(rideOfferRepository.findByIdForUpdate(offer.getId())).thenReturn(Optional.of(offer));
        Booking confirmed = booking(UUID.randomUUID());
        when(bookingRepository.findByRideOfferIdAndStatus(offer.getId(), BookingStatus.CONFIRMED))
                .thenReturn(List.of(confirmed));

        service.startRide(offer.getId(), driver);

        assertThat(offer.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        verify(notificationService).notifyUser(eq(confirmed.getPassengerId()), any(), any(),
                eq(NotificationType.RIDE_STARTED), eq(offer.getId()));
    }
}
