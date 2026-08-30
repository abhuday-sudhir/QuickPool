package com.QuickPool.controller;

import com.QuickPool.dtos.*;
import com.QuickPool.exception.TooManyRequestsException;
import com.QuickPool.service.OtpService;
import com.QuickPool.service.RateLimiter;
import com.QuickPool.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private OtpService otpService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RateLimiter rateLimiter;

    @PostMapping("/otp/request")
    public String requestOtp(@Valid @RequestBody OtpRequestDto dto, HttpServletRequest request) {
        // Two budgets: one stops a single number being spammed, the other stops one
        // machine walking through many numbers.
        requireWithin("otp-phone", dto.getPhone(), 5, Duration.ofHours(1),
                "Too many codes requested for this number. Try again later.");
        requireWithin("otp-ip", clientIp(request), 20, Duration.ofHours(1),
                "Too many requests from this device. Try again later.");

        otpService.requestOtp(dto.getPhone());
        return "OTP sent"; // check your terminal/console logs for the actual code
    }

    @PostMapping("/otp/verify")
    public AuthResponseDto verifyOtp(@Valid @RequestBody OtpVerifyDto dto, HttpServletRequest request) {
        requireWithin("otp-verify", clientIp(request), 30, Duration.ofHours(1),
                "Too many attempts. Try again later.");

        var result = otpService.verifyOtp(dto.getPhone(), dto.getOtp());
        return tokenService.issuePair(result.userId(), result.profileComplete());
    }

    @PostMapping("/refresh")
    public AuthResponseDto refresh(@RequestBody RefreshTokenDto dto, HttpServletRequest request) {
        requireWithin("refresh", clientIp(request), 60, Duration.ofHours(1),
                "Too many refresh attempts. Try again later.");
        return tokenService.rotate(dto.getRefreshToken());
    }

    private void requireWithin(String scope, String key, int limit, Duration window, String message) {
        if (!rateLimiter.allow(scope, key, limit, window)) {
            throw new TooManyRequestsException(message);
        }
    }

    /** Behind a proxy the socket address is the proxy, so prefer the forwarded header. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
