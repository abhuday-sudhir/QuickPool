package com.QuickRide.controller;

import com.QuickRide.dtos.*;
import com.QuickRide.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private OtpService otpService;

    @PostMapping("/otp/request")
    public String requestOtp(@RequestBody OtpRequestDto dto) {
        otpService.requestOtp(dto.getPhone());
        return "OTP sent"; // check your terminal/console logs for the actual code
    }

    @PostMapping("/otp/verify")
    public AuthResponseDto verifyOtp(@RequestBody OtpVerifyDto dto) {
        var result = otpService.verifyOtp(dto.getPhone(), dto.getOtp());
        return new AuthResponseDto(result.userId(), result.accessToken(), result.refreshToken());
    }
}