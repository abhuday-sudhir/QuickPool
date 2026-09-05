package com.QuickPool.service;

import com.QuickPool.dtos.BookingRequestDto;
import com.QuickPool.dtos.BookingWithRideDto;
import com.QuickPool.dtos.CreateBookingDto;
import com.QuickPool.dtos.PageResponseDto;
import com.QuickPool.entity.Booking;
import com.QuickPool.entity.RideOffer;
import com.QuickPool.entity.User;
import com.QuickPool.enums.BookingStatus;
import com.QuickPool.enums.RideStatus;
import com.QuickPool.exception.ConflictException;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.BookingRepository;
import com.QuickPool.repository.RideOfferRepository;
import com.QuickPool.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PRODUCTION_TASKS.md 3.2 (pagination) and 3.3 (N+1 queries): getMyBookings and
 * getBookingRequestsForDriver used to call rideOfferRepository.findById once per row inside
 * a .map() — these tests lock in the batched replacement (one findAllById per page) and the
 * Slice -> PageResponseDto mapping (hasNext carried through, empty short-circuit for a driver
 * with no rides at all).
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private RideOfferRepository rideOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private SafetyService safetyService;
    @Mock private ActivityLogService activityLogService;
    @InjectMocks private BookingService service;

    private final UUID passenger = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();

    private RideOffer ride(UUID id) {
        RideOffer r = new RideOffer();
        r.setId(id);
        r.setDriverId(driver);
        r.setStatus(RideStatus.ACTIVE);
        r.setDepartureTime(LocalDateTime.now().plusHours(1));
        return r;
    }

    private Booking bookingOn(UUID rideId) {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setRideOfferId(rideId);
        b.setPassengerId(passenger);
        b.setSeatsBooked((short) 1);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setCreatedAt(LocalDateTime.now());
        return b;
    }

    private CreateBookingDto bookingDto(UUID rideId, int seats) {
        CreateBookingDto dto = new CreateBookingDto();
        dto.setRideOfferId(rideId);
        dto.setSeatsBooked((short) seats);
        return dto;
    }

    @Test
    @DisplayName("getMyBookings() fetches every ride on the page in one findAllById, not one findById per booking")
    void myBookingsBatchesRideLookup() {
        UUID ride1 = UUID.randomUUID(), ride2 = UUID.randomUUID();
        var slice = new SliceImpl<>(List.of(bookingOn(ride1), bookingOn(ride2)), PageRequest.of(0, 20), false);
        when(bookingRepository.findByPassengerIdOrderByCreatedAtDesc(eq(passenger), any())).thenReturn(slice);
        when(rideOfferRepository.findAllById(any())).thenReturn(List.of(ride(ride1), ride(ride2)));

        PageResponseDto<BookingWithRideDto> page = service.getMyBookings(passenger, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.isHasNext()).isFalse();
        verify(rideOfferRepository, times(1)).findAllById(any());
        verify(rideOfferRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getBookingRequestsForDriver() batches both ride and passenger lookups for the page")
    void bookingRequestsBatchesLookups() {
        UUID ride1 = UUID.randomUUID();
        when(rideOfferRepository.findByDriverId(driver)).thenReturn(List.of(ride(ride1)));
        var slice = new SliceImpl<>(List.of(bookingOn(ride1)), PageRequest.of(0, 20), true);
        when(bookingRepository.findByRideOfferIdInOrderByCreatedAtDesc(eq(List.of(ride1)), any())).thenReturn(slice);
        when(rideOfferRepository.findAllById(any())).thenReturn(List.of(ride(ride1)));
        User p = new User(); p.setId(passenger); p.setName("Test Passenger");
        when(userRepository.findAllById(any())).thenReturn(List.of(p));

        PageResponseDto<BookingRequestDto> page = service.getBookingRequestsForDriver(driver, PageRequest.of(0, 20));

        assertThat(page.isHasNext()).isTrue();
        assertThat(page.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.getPassengerName()).isEqualTo("Test Passenger"));
        verify(rideOfferRepository, times(1)).findAllById(any());
        verify(userRepository, times(1)).findAllById(any());
        verify(rideOfferRepository, never()).findById(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getBookingRequestsForDriver() short-circuits to an empty page for a driver with no rides")
    void bookingRequestsEmptyWhenNoRides() {
        when(rideOfferRepository.findByDriverId(driver)).thenReturn(List.of());

        PageResponseDto<BookingRequestDto> page = service.getBookingRequestsForDriver(driver, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.isHasNext()).isFalse();
        verifyNoInteractions(bookingRepository);
    }

    // --- bookRide() -----------------------------------------------------------------

    @Test
    @DisplayName("bookRide() refuses a driver booking their own ride")
    void bookRideRefusesSelf() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.bookRide(bookingDto(rideId, 1), driver))
                .isInstanceOf(ForbiddenException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("bookRide() refuses a passenger the driver has blocked (in either direction)")
    void bookRideRefusesBlocked() {
        UUID rideId = UUID.randomUUID();
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(ride(rideId)));
        when(safetyService.isHidden(passenger, driver)).thenReturn(true);

        assertThatThrownBy(() -> service.bookRide(bookingDto(rideId, 1), passenger))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("bookRide() refuses a ride that isn't ACTIVE")
    void bookRideRefusesInactiveRide() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        offer.setStatus(RideStatus.FULL);
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.bookRide(bookingDto(rideId, 1), passenger))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("bookRide() refuses a request for more seats than are left")
    void bookRideRefusesNotEnoughSeats() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        offer.setSeatsAvailable((short) 1);
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.bookRide(bookingDto(rideId, 2), passenger))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("bookRide() refuses a second booking on the same ride by the same passenger")
    void bookRideRefusesDuplicate() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        offer.setSeatsAvailable((short) 3);
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));
        when(bookingRepository.existsByRideOfferIdAndPassengerIdAndStatusIn(eq(rideId), eq(passenger), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.bookRide(bookingDto(rideId, 1), passenger))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("bookRide() holds the seat immediately, filling the ride when it takes the last one")
    void bookRideHoldsLastSeat() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        offer.setSeatsAvailable((short) 1);
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));
        when(bookingRepository.existsByRideOfferIdAndPassengerIdAndStatusIn(eq(rideId), eq(passenger), any()))
                .thenReturn(false);

        service.bookRide(bookingDto(rideId, 1), passenger);

        assertThat(offer.getSeatsAvailable()).isZero();
        assertThat(offer.getStatus()).isEqualTo(RideStatus.FULL);
        var savedBooking = org.mockito.ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(savedBooking.capture());
        assertThat(savedBooking.getValue().getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(notificationService).notifyUser(eq(driver), any(), any(), any(), any());
    }

    // --- acceptBooking() / rejectBooking() -------------------------------------------

    @Test
    @DisplayName("acceptBooking() refuses someone who doesn't drive this ride")
    void acceptRefusesNonOwner() {
        UUID rideId = UUID.randomUUID();
        Booking booking = bookingOn(rideId);
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride(rideId)));

        assertThatThrownBy(() -> service.acceptBooking(booking.getId(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("acceptBooking() refuses a request that is no longer pending")
    void acceptRefusesNotPending() {
        UUID rideId = UUID.randomUUID();
        Booking booking = bookingOn(rideId);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride(rideId)));

        assertThatThrownBy(() -> service.acceptBooking(booking.getId(), driver))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("acceptBooking() confirms a pending request and notifies the passenger")
    void acceptHappyPath() {
        UUID rideId = UUID.randomUUID();
        Booking booking = bookingOn(rideId);
        booking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(ride(rideId)));

        service.acceptBooking(booking.getId(), driver);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(notificationService).notifyUser(eq(passenger), any(), any(), any(), eq(booking.getId()));
    }

    @Test
    @DisplayName("rejectBooking() releases the held seat and reopens a FULL ride")
    void rejectReleasesSeat() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        offer.setSeatsAvailable((short) 0);
        offer.setStatus(RideStatus.FULL);
        Booking booking = bookingOn(rideId);
        booking.setStatus(BookingStatus.PENDING);
        booking.setSeatsBooked((short) 1);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(rideOfferRepository.findById(rideId)).thenReturn(Optional.of(offer));
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));

        service.rejectBooking(booking.getId(), driver);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(offer.getSeatsAvailable()).isEqualTo((short) 1);
        assertThat(offer.getStatus()).isEqualTo(RideStatus.ACTIVE);
        verify(notificationService).notifyUser(eq(passenger), any(), any(), any(), eq(booking.getId()));
    }

    // --- cancelBooking() --------------------------------------------------------------

    @Test
    @DisplayName("cancelBooking() refuses to cancel someone else's booking")
    void cancelRefusesNonOwner() {
        UUID rideId = UUID.randomUUID();
        Booking booking = bookingOn(rideId);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(booking.getId(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancelBooking() refuses a booking that is no longer active")
    void cancelRefusesInactive() {
        UUID rideId = UUID.randomUUID();
        Booking booking = bookingOn(rideId);
        booking.setStatus(BookingStatus.REJECTED);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(booking.getId(), passenger))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("cancelBooking() releases the seat and notifies the driver")
    void cancelHappyPath() {
        UUID rideId = UUID.randomUUID();
        RideOffer offer = ride(rideId);
        offer.setSeatsAvailable((short) 2);
        Booking booking = bookingOn(rideId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSeatsBooked((short) 1);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(rideOfferRepository.findByIdForUpdate(rideId)).thenReturn(Optional.of(offer));
        when(rideOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancelBooking(booking.getId(), passenger);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(offer.getSeatsAvailable()).isEqualTo((short) 3);
        verify(notificationService).notifyUser(eq(driver), any(), any(), any(), eq(booking.getId()));
    }

    @Test
    @DisplayName("cancelBooking() 404s for a booking id that doesn't exist")
    void cancelMissingBooking() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelBooking(bookingId, passenger))
                .isInstanceOf(NotFoundException.class);
    }
}
