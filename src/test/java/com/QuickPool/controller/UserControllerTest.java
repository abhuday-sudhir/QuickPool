package com.QuickPool.controller;

import com.QuickPool.dtos.DestinationDto;
import com.QuickPool.dtos.ImpactDto;
import com.QuickPool.dtos.SaveAddressDto;
import com.QuickPool.dtos.SaveVehicleDto;
import com.QuickPool.dtos.SavedAddressDto;
import com.QuickPool.dtos.VehicleDto;
import com.QuickPool.entity.User;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.repository.UserRepository;
import com.QuickPool.service.AccountDeletionService;
import com.QuickPool.service.DestinationService;
import com.QuickPool.service.EmailVerificationService;
import com.QuickPool.service.ImpactService;
import com.QuickPool.service.SavedAddressService;
import com.QuickPool.service.TokenService;
import com.QuickPool.service.VehicleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTestSupport {

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private DestinationService destinationService;
    @MockitoBean private SavedAddressService savedAddressService;
    @MockitoBean private ImpactService impactService;
    @MockitoBean private VehicleService vehicleService;
    @MockitoBean private EmailVerificationService emailVerificationService;
    @MockitoBean private AccountDeletionService accountDeletionService;
    @MockitoBean private TokenService tokenService;

    private User user(String name, String email) {
        User u = new User();
        u.setId(userId);
        u.setPhone("+919000000001");
        u.setName(name);
        u.setEmail(email);
        u.setRatingAvg(new BigDecimal("4.5"));
        return u;
    }

    // --- profile ---

    @Test
    @DisplayName("GET /me returns the caller's profile")
    void me() throws Exception {
        User u = user("Asha", "asha@example.com");
        u.setEmailVerified(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));

        mvc.perform(get("/api/v1/users/me").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.phone").value("+919000000001"))
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("GET /me for a fresh signup reports the profile as incomplete")
    void meIncomplete() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(null, null)));

        mvc.perform(get("/api/v1/users/me").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("GET /me for a token whose user no longer exists is a 404")
    void meNotFound() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/me").with(authed()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("GET /me needs a token")
    void meUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("PUT /me completes the profile, trimming the name")
    void updateMe() throws Exception {
        User u = user(null, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        mvc.perform(put("/api/v1/users/me").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Asha  ", "email": "asha@example.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.profileComplete").value(true));

        assertThat(u.getName()).isEqualTo("Asha");
        assertThat(u.getEmail()).isEqualTo("asha@example.com");
    }

    @Test
    @DisplayName("PUT /me with a one-letter name and a bad email fails validation")
    void updateMeInvalid() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "A", "email": "not-an-email"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name must be 2-80 characters"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Enter a valid email address"));

        verify(userRepository, never()).save(any());
    }

    // --- destinations & addresses ---

    @Test
    @DisplayName("GET /me/destinations returns frequent destinations")
    void frequentDestinations() throws Exception {
        when(destinationService.getFrequent(userId)).thenReturn(List.of(new DestinationDto("Office", 28.5, 77.1, 12)));

        mvc.perform(get("/api/v1/users/me/destinations").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Office"))
                .andExpect(jsonPath("$[0].useCount").value(12));
    }

    @Test
    @DisplayName("GET /me/destinations/recent returns recent destinations")
    void recentDestinations() throws Exception {
        when(destinationService.getRecent(userId)).thenReturn(List.of(new DestinationDto("Gym", 28.4, 77.0, 1)));

        mvc.perform(get("/api/v1/users/me/destinations/recent").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Gym"));
    }

    @Test
    @DisplayName("POST /me/destinations records a destination")
    void recordDestination() throws Exception {
        mvc.perform(post("/api/v1/users/me/destinations").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Office", "lat": 28.5, "lng": 77.1}"""))
                .andExpect(status().isOk());

        verify(destinationService).record(userId, "Office", 28.5, 77.1);
    }

    @Test
    @DisplayName("a destination with out-of-range latitude fails validation")
    void recordDestinationInvalid() throws Exception {
        mvc.perform(post("/api/v1/users/me/destinations").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Office", "lat": 95.0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat").exists())
                .andExpect(jsonPath("$.fieldErrors.lng").value("lng is required"));

        verify(destinationService, never()).record(any(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("GET /me/addresses lists saved addresses")
    void addresses() throws Exception {
        when(savedAddressService.list(userId)).thenReturn(List.of(new SavedAddressDto("a1", "Home", "12 MG Road", 28.6, 77.2)));

        mvc.perform(get("/api/v1/users/me/addresses").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Home"));
    }

    @Test
    @DisplayName("POST /me/addresses saves an address")
    void saveAddress() throws Exception {
        when(savedAddressService.save(eq(userId), any()))
                .thenReturn(new SavedAddressDto("a1", "Home", "12 MG Road", 28.6, 77.2));

        mvc.perform(post("/api/v1/users/me/addresses").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "Home", "name": "12 MG Road", "lat": 28.6, "lng": 77.2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("a1"));

        ArgumentCaptor<SaveAddressDto> dto = ArgumentCaptor.forClass(SaveAddressDto.class);
        verify(savedAddressService).save(eq(userId), dto.capture());
        assertThat(dto.getValue().getLabel()).isEqualTo("Home");
    }

    @Test
    @DisplayName("an address without a label fails validation")
    void saveAddressWithoutLabel() throws Exception {
        mvc.perform(post("/api/v1/users/me/addresses").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "12 MG Road", "lat": 28.6, "lng": 77.2}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.label").value("Give this address a label, e.g. Home"));

        verifyNoInteractions(savedAddressService);
    }

    @Test
    @DisplayName("DELETE /me/addresses/{id} deletes the caller's address")
    void deleteAddress() throws Exception {
        UUID addressId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/users/me/addresses/{id}", addressId).with(authed()))
                .andExpect(status().isOk());

        verify(savedAddressService).delete(userId, addressId);
    }

    // --- impact, email, session, account ---

    @Test
    @DisplayName("GET /me/impact returns the caller's impact stats")
    void impact() throws Exception {
        when(impactService.forUser(userId)).thenReturn(new ImpactDto(7, 84.5, 10.1, 0.5));

        mvc.perform(get("/api/v1/users/me/impact").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedRides").value(7))
                .andExpect(jsonPath("$.co2SavedKg").value(10.1));
    }

    @Test
    @DisplayName("POST /me/email/verify/request sends a code")
    void requestEmailVerification() throws Exception {
        mvc.perform(post("/api/v1/users/me/email/verify/request").with(authed()))
                .andExpect(status().isOk());

        verify(emailVerificationService).requestCode(userId);
    }

    @Test
    @DisplayName("POST /me/email/verify confirms the code")
    void confirmEmailVerification() throws Exception {
        mvc.perform(post("/api/v1/users/me/email/verify").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "123456"}"""))
                .andExpect(status().isOk());

        verify(emailVerificationService).confirm(userId, "123456");
    }

    @Test
    @DisplayName("an email code that is not six digits fails validation")
    void confirmEmailVerificationMalformed() throws Exception {
        mvc.perform(post("/api/v1/users/me/email/verify").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "12"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").value("The code is 6 digits"));

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    @DisplayName("a wrong email code surfaces the service's 400")
    void confirmEmailVerificationWrongCode() throws Exception {
        doThrow(new BadRequestException("Incorrect code")).when(emailVerificationService).confirm(userId, "000000");

        mvc.perform(post("/api/v1/users/me/email/verify").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "000000"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Incorrect code"));
    }

    @Test
    @DisplayName("POST /me/logout revokes every refresh token server-side")
    void logout() throws Exception {
        mvc.perform(post("/api/v1/users/me/logout").with(authed()))
                .andExpect(status().isOk());

        verify(tokenService).revokeAll(userId, "LOGOUT");
    }

    @Test
    @DisplayName("DELETE /me deletes the caller's own account")
    void deleteAccount() throws Exception {
        mvc.perform(delete("/api/v1/users/me").with(authed()))
                .andExpect(status().isOk());

        verify(accountDeletionService).deleteOwnAccount(userId);
    }

    @Test
    @DisplayName("deleting an account needs a token")
    void deleteAccountUnauthenticated() throws Exception {
        mvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountDeletionService);
    }

    // --- vehicle ---

    @Test
    @DisplayName("GET /me/vehicle returns the caller's vehicle")
    void vehicle() throws Exception {
        when(vehicleService.forUser(userId)).thenReturn(new VehicleDto("Maruti", "Swift", "White", "DL3CAB1234"));

        mvc.perform(get("/api/v1/users/me/vehicle").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Maruti"))
                .andExpect(jsonPath("$.plate").value("DL3CAB1234"));
    }

    @Test
    @DisplayName("PUT /me/vehicle saves the vehicle")
    void saveVehicle() throws Exception {
        when(vehicleService.save(eq(userId), any())).thenReturn(new VehicleDto("Maruti", "Swift", "White", "DL3CAB1234"));

        mvc.perform(put("/api/v1/users/me/vehicle").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"make": "Maruti", "model": "Swift", "color": "White", "plate": "DL3CAB1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Swift"));

        ArgumentCaptor<SaveVehicleDto> dto = ArgumentCaptor.forClass(SaveVehicleDto.class);
        verify(vehicleService).save(eq(userId), dto.capture());
        assertThat(dto.getValue().getPlate()).isEqualTo("DL3CAB1234");
    }

    @Test
    @DisplayName("a plate with symbols in it fails validation")
    void saveVehicleBadPlate() throws Exception {
        mvc.perform(put("/api/v1/users/me/vehicle").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"make": "Maruti", "model": "Swift", "color": "White", "plate": "DL#3C!"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.plate").value("That doesn't look like a number plate"));

        verifyNoInteractions(vehicleService);
    }

    @Test
    @DisplayName("DELETE /me/vehicle removes the vehicle")
    void deleteVehicle() throws Exception {
        mvc.perform(delete("/api/v1/users/me/vehicle").with(authed()))
                .andExpect(status().isOk());

        verify(vehicleService).delete(userId);
    }
}
