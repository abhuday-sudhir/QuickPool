package com.QuickPool.controller;

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
import com.QuickPool.service.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
class BookingControllerTest extends ControllerTestSupport {

    @MockitoBean private BookingService bookingService;

    private final UUID rideId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    private RideOffer ride() {
        RideOffer r = new RideOffer();
        r.setId(rideId);
        r.setStatus(RideStatus.ACTIVE);
        r.setDepartureTime(LocalDateTime.of(2030, 1, 1, 9, 0));
        return r;
    }

    private Booking booking(BookingStatus status) {
        Booking b = new Booking();
        b.setId(bookingId);
        b.setRideOfferId(rideId);
        b.setPassengerId(UUID.randomUUID());
        b.setSeatsBooked((short) 2);
        b.setStatus(status);
        b.setCreatedAt(LocalDateTime.of(2029, 12, 31, 18, 0));
        return b;
    }

    @Test
    @DisplayName("POST /bookings books as the authenticated passenger and returns the booking id")
    void book() throws Exception {
        when(bookingService.bookRide(any(), eq(userId))).thenReturn(bookingId);

        mvc.perform(post("/api/v1/bookings").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s", "seatsBooked": 2}""".formatted(rideId)))
                .andExpect(status().isOk())
                .andExpect(content().string("\"" + bookingId + "\""));

        ArgumentCaptor<CreateBookingDto> dto = ArgumentCaptor.forClass(CreateBookingDto.class);
        verify(bookingService).bookRide(dto.capture(), eq(userId));
        assertThat(dto.getValue().getRideOfferId()).isEqualTo(rideId);
        assertThat(dto.getValue().getSeatsBooked()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("booking without a ride id or with zero seats fails validation")
    void bookInvalid() throws Exception {
        mvc.perform(post("/api/v1/bookings").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatsBooked": 0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.rideOfferId").value("rideOfferId is required"))
                .andExpect(jsonPath("$.fieldErrors.seatsBooked").value("seatsBooked must be at least 1"));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("a full ride surfaces the service's 409")
    void bookConflict() throws Exception {
        when(bookingService.bookRide(any(), eq(userId))).thenThrow(new ConflictException("Not enough seats"));

        mvc.perform(post("/api/v1/bookings").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s"}""".formatted(rideId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Not enough seats"));
    }

    @Test
    @DisplayName("booking without a token is a 401")
    void bookUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s"}""".formatted(rideId)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("an invalid token is treated as no token")
    void bookWithInvalidToken() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s"}""".formatted(rideId)))
                .andExpect(status().isUnauthorized());

        verify(jwtService, never()).extractUserId("expired");
    }

    @Test
    @DisplayName("a non-Bearer Authorization header is ignored rather than parsed as a JWT")
    void bookWithNonBearerHeader() throws Exception {
        mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rideOfferId": "%s"}""".formatted(rideId)))
                .andExpect(status().isUnauthorized());

        verify(jwtService, never()).isValid(any());
    }

    @Test
    @DisplayName("PUT /{id}/cancel cancels as the passenger")
    void cancel() throws Exception {
        mvc.perform(put("/api/v1/bookings/{id}/cancel", bookingId).with(authed()))
                .andExpect(status().isOk());

        verify(bookingService).cancelBooking(bookingId, userId);
    }

    @Test
    @DisplayName("cancelling someone else's booking surfaces the service's 403")
    void cancelForbidden() throws Exception {
        doThrow(new ForbiddenException("Not your booking")).when(bookingService).cancelBooking(bookingId, userId);

        mvc.perform(put("/api/v1/bookings/{id}/cancel", bookingId).with(authed()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PUT /{id}/accept accepts as the driver")
    void accept() throws Exception {
        mvc.perform(put("/api/v1/bookings/{id}/accept", bookingId).with(authed()))
                .andExpect(status().isOk());

        verify(bookingService).acceptBooking(bookingId, userId);
    }

    @Test
    @DisplayName("PUT /{id}/reject rejects as the driver")
    void reject() throws Exception {
        mvc.perform(put("/api/v1/bookings/{id}/reject", bookingId).with(authed()))
                .andExpect(status().isOk());

        verify(bookingService).rejectBooking(bookingId, userId);
    }

    @Test
    @DisplayName("GET /mine pages the passenger's bookings with a default page size of 20")
    void myBookings() throws Exception {
        when(bookingService.getMyBookings(eq(userId), any())).thenReturn(new PageResponseDto<>(
                List.of(new BookingWithRideDto(booking(BookingStatus.CONFIRMED), ride())), true));

        mvc.perform(get("/api/v1/bookings/mine").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.content[0].rideOfferId").value(rideId.toString()))
                .andExpect(jsonPath("$.content[0].bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].rideStatus").value("ACTIVE"));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).getMyBookings(eq(userId), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("GET /mine honours page and size query params")
    void myBookingsPaged() throws Exception {
        when(bookingService.getMyBookings(eq(userId), any())).thenReturn(new PageResponseDto<>(List.of(), false));

        mvc.perform(get("/api/v1/bookings/mine").with(authed()).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingService).getMyBookings(eq(userId), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("GET /requests lists bookings on the driver's rides")
    void bookingRequests() throws Exception {
        User passenger = new User();
        passenger.setName("Priya");
        passenger.setPhone("+919000000002");
        when(bookingService.getBookingRequestsForDriver(eq(userId), any())).thenReturn(new PageResponseDto<>(
                List.of(new BookingRequestDto(booking(BookingStatus.PENDING), ride(), passenger)), false));

        mvc.perform(get("/api/v1/bookings/requests").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.content[0].passengerName").value("Priya"))
                .andExpect(jsonPath("$.content[0].seatsBooked").value(2))
                .andExpect(jsonPath("$.content[0].bookingStatus").value("PENDING"))
                // A pending request must not leak the passenger's phone number.
                .andExpect(jsonPath("$.content[0].passengerPhone").doesNotExist());
    }
}
