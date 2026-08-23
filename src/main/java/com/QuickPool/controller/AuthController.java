package com.QuickPool.controller;

import com.QuickPool.dtos.*;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.service.JwtService;
import com.QuickPool.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private OtpService otpService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/otp/request")
    public String requestOtp(@Valid @RequestBody OtpRequestDto dto) {
        otpService.requestOtp(dto.getPhone());
        return "OTP sent"; // check your terminal/console logs for the actual code
    }

    @PostMapping("/otp/verify")
    public AuthResponseDto verifyOtp(@Valid @RequestBody OtpVerifyDto dto) {
        var result = otpService.verifyOtp(dto.getPhone(), dto.getOtp());
        return new AuthResponseDto(result.userId(), result.accessToken(), result.refreshToken());
    }

    @PostMapping("/refresh")
    public AuthResponseDto refresh(@RequestBody RefreshTokenDto dto) {
        if (!jwtService.isValid(dto.getRefreshToken())) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (!"refresh".equals(jwtService.extractType(dto.getRefreshToken()))) {
            throw new BadRequestException("Not a refresh token");
        }

        UUID userId = jwtService.extractUserId(dto.getRefreshToken());
        String newAccessToken = jwtService.generateAccessToken(userId);
        String newRefreshToken = jwtService.generateRefreshToken(userId);

        return new AuthResponseDto(userId, newAccessToken, newRefreshToken);
    }
}