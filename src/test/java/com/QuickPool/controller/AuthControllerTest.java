package com.QuickPool.controller;

import com.QuickPool.dtos.AuthResponseDto;
import com.QuickPool.exception.BadRequestException;
import com.QuickPool.service.OtpService;
import com.QuickPool.service.RateLimiter;
import com.QuickPool.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The auth routes are the only ones open without a token, and each is rate limited on its own
 * budget — these tests cover both limits on /otp/request (per number, per client IP), that the
 * client IP comes from X-Forwarded-For when a proxy sets it, and that a tripped limit is a 429.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTestSupport {

    @MockitoBean private OtpService otpService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private RateLimiter rateLimiter;

    private static final String PHONE = "+919000000001";

    @BeforeEach
    void allowByDefault() {
        when(rateLimiter.allow(anyString(), anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("POST /otp/request sends a code without needing a token")
    void requestOtp() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}""".formatted(PHONE)))
                .andExpect(status().isOk())
                .andExpect(content().string("OTP sent"));

        verify(otpService).requestOtp(PHONE);
        verify(rateLimiter).allow("otp-phone", PHONE, 5, Duration.ofHours(1));
    }

    @Test
    @DisplayName("the per-IP budget is keyed on the first X-Forwarded-For address")
    void requestOtpUsesForwardedIp() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .header("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}""".formatted(PHONE)))
                .andExpect(status().isOk());

        verify(rateLimiter).allow("otp-ip", "203.0.113.7", 20, Duration.ofHours(1));
    }

    @Test
    @DisplayName("without X-Forwarded-For the per-IP budget falls back to the socket address")
    void requestOtpUsesRemoteAddr() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .with(request -> { request.setRemoteAddr("198.51.100.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}""".formatted(PHONE)))
                .andExpect(status().isOk());

        verify(rateLimiter).allow("otp-ip", "198.51.100.4", 20, Duration.ofHours(1));
    }

    @Test
    @DisplayName("a blank X-Forwarded-For is ignored in favour of the socket address")
    void requestOtpIgnoresBlankForwardedHeader() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .header("X-Forwarded-For", "  ")
                        .with(request -> { request.setRemoteAddr("198.51.100.4"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}""".formatted(PHONE)))
                .andExpect(status().isOk());

        verify(rateLimiter).allow("otp-ip", "198.51.100.4", 20, Duration.ofHours(1));
    }

    @Test
    @DisplayName("too many codes for one number is a 429 and no code is sent")
    void requestOtpPhoneLimited() throws Exception {
        when(rateLimiter.allow(eq("otp-phone"), anyString(), anyInt(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}""".formatted(PHONE)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("Too many codes requested for this number. Try again later."));

        verifyNoInteractions(otpService);
    }

    @Test
    @DisplayName("too many requests from one IP is a 429 and no code is sent")
    void requestOtpIpLimited() throws Exception {
        when(rateLimiter.allow(eq("otp-ip"), anyString(), anyInt(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s"}""".formatted(PHONE)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests from this device. Try again later."));

        verifyNoInteractions(otpService);
    }

    @Test
    @DisplayName("a phone number not in E.164 format is rejected before any work is done")
    void requestOtpInvalidPhone() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "9000000001"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.phone").exists());

        verifyNoInteractions(otpService, rateLimiter);
    }

    @Test
    @DisplayName("POST /otp/verify issues a token pair for the verified user")
    void verifyOtp() throws Exception {
        var verifiedId = java.util.UUID.randomUUID();
        when(otpService.verifyOtp(PHONE, "123456")).thenReturn(new OtpService.VerifiedUser(verifiedId, false));
        when(tokenService.issuePair(verifiedId, false))
                .thenReturn(new AuthResponseDto(verifiedId, "access", "refresh", false));

        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "otp": "123456"}""".formatted(PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(verifiedId.toString()))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.profileComplete").value(false));
    }

    @Test
    @DisplayName("a wrong code surfaces the service's 400")
    void verifyOtpWrongCode() throws Exception {
        when(otpService.verifyOtp(PHONE, "000000")).thenThrow(new BadRequestException("Invalid OTP"));

        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "otp": "000000"}""".formatted(PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid OTP"));

        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("an OTP that is not six digits fails validation")
    void verifyOtpMalformed() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "otp": "12ab"}""".formatted(PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.otp").value("otp must be exactly 6 digits"));
    }

    @Test
    @DisplayName("verify attempts are rate limited per IP")
    void verifyOtpLimited() throws Exception {
        when(rateLimiter.allow(eq("otp-verify"), anyString(), anyInt(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone": "%s", "otp": "123456"}""".formatted(PHONE)))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(otpService);
    }

    @Test
    @DisplayName("POST /refresh rotates the refresh token")
    void refresh() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(tokenService.rotate("old-refresh")).thenReturn(new AuthResponseDto(id, "a2", "r2", true));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "old-refresh"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a2"))
                .andExpect(jsonPath("$.refreshToken").value("r2"));
    }

    @Test
    @DisplayName("refresh attempts are rate limited per IP")
    void refreshLimited() throws Exception {
        when(rateLimiter.allow(eq("refresh"), anyString(), anyInt(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "old-refresh"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many refresh attempts. Try again later."));

        verifyNoInteractions(tokenService);
    }
}
