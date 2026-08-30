package com.QuickPool.controller;

import com.QuickPool.dtos.DestinationDto;
import com.QuickPool.dtos.ImpactDto;
import com.QuickPool.dtos.RecordDestinationDto;
import com.QuickPool.dtos.SaveAddressDto;
import com.QuickPool.dtos.SaveVehicleDto;
import com.QuickPool.dtos.VehicleDto;
import com.QuickPool.dtos.SavedAddressDto;
import com.QuickPool.dtos.UpdateProfileDto;
import com.QuickPool.dtos.UserResponseDto;
import com.QuickPool.dtos.VerifyEmailDto;
import com.QuickPool.entity.User;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.UserRepository;
import com.QuickPool.service.DestinationService;
import com.QuickPool.service.ImpactService;
import com.QuickPool.service.AccountDeletionService;
import com.QuickPool.service.EmailVerificationService;
import com.QuickPool.service.TokenService;
import com.QuickPool.service.VehicleService;
import com.QuickPool.service.SavedAddressService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DestinationService destinationService;

    @Autowired
    private SavedAddressService savedAddressService;

    @Autowired
    private ImpactService impactService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private TokenService tokenService;

    @GetMapping("/me")
    public UserResponseDto me(Authentication auth) {
        return new UserResponseDto(currentUser(auth));
    }

    /** Completes registration for a new user, and doubles as profile editing later. */
    @PutMapping("/me")
    public UserResponseDto updateMe(@Valid @RequestBody UpdateProfileDto dto, Authentication auth) {
        User user = currentUser(auth);
        user.setName(dto.getName().trim());
        user.setEmail(dto.getEmail().trim());
        return new UserResponseDto(userRepository.save(user));
    }

    @GetMapping("/me/destinations")
    public List<DestinationDto> myDestinations(Authentication auth) {
        return destinationService.getFrequent((UUID) auth.getPrincipal());
    }

    @GetMapping("/me/destinations/recent")
    public List<DestinationDto> myRecentDestinations(Authentication auth) {
        return destinationService.getRecent((UUID) auth.getPrincipal());
    }

    @PostMapping("/me/destinations")
    public void recordDestination(@Valid @RequestBody RecordDestinationDto dto, Authentication auth) {
        destinationService.record((UUID) auth.getPrincipal(), dto.getName(), dto.getLat(), dto.getLng());
    }

    @GetMapping("/me/addresses")
    public List<SavedAddressDto> myAddresses(Authentication auth) {
        return savedAddressService.list((UUID) auth.getPrincipal());
    }

    @PostMapping("/me/addresses")
    public SavedAddressDto saveAddress(@Valid @RequestBody SaveAddressDto dto, Authentication auth) {
        return savedAddressService.save((UUID) auth.getPrincipal(), dto);
    }

    @DeleteMapping("/me/addresses/{id}")
    public void deleteAddress(@PathVariable UUID id, Authentication auth) {
        savedAddressService.delete((UUID) auth.getPrincipal(), id);
    }

    @GetMapping("/me/impact")
    public ImpactDto myImpact(Authentication auth) {
        return impactService.forUser((UUID) auth.getPrincipal());
    }

    @PostMapping("/me/email/verify/request")
    public void requestEmailVerification(Authentication auth) {
        emailVerificationService.requestCode((UUID) auth.getPrincipal());
    }

    @PostMapping("/me/email/verify")
    public void confirmEmailVerification(@Valid @RequestBody VerifyEmailDto dto, Authentication auth) {
        emailVerificationService.confirm((UUID) auth.getPrincipal(), dto.getCode());
    }

    /** Signing out invalidates the refresh token server-side, not just on the device. */
    @PostMapping("/me/logout")
    public void logout(Authentication auth) {
        tokenService.revokeAll((UUID) auth.getPrincipal(), "LOGOUT");
    }

    @DeleteMapping("/me")
    public void deleteAccount(Authentication auth) {
        accountDeletionService.deleteOwnAccount((UUID) auth.getPrincipal());
    }

    @GetMapping("/me/vehicle")
    public VehicleDto myVehicle(Authentication auth) {
        return vehicleService.forUser((UUID) auth.getPrincipal());
    }

    @PutMapping("/me/vehicle")
    public VehicleDto saveVehicle(@Valid @RequestBody SaveVehicleDto dto, Authentication auth) {
        return vehicleService.save((UUID) auth.getPrincipal(), dto);
    }

    @DeleteMapping("/me/vehicle")
    public void deleteVehicle(Authentication auth) {
        vehicleService.delete((UUID) auth.getPrincipal());
    }

    private User currentUser(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
