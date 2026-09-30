package com.QuickPool.controller;

import com.QuickPool.service.DeviceTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest extends ControllerTestSupport {

    @MockitoBean private DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("POST /devices registers the token against the caller")
    void register() throws Exception {
        mvc.perform(post("/api/v1/devices").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token", "platform": "ios"}"""))
                .andExpect(status().isOk());

        verify(deviceTokenService).register(userId, "fcm-token", "ios");
    }

    @Test
    @DisplayName("platform defaults to android when the app leaves it out")
    void registerDefaultsPlatform() throws Exception {
        mvc.perform(post("/api/v1/devices").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token"}"""))
                .andExpect(status().isOk());

        verify(deviceTokenService).register(userId, "fcm-token", "android");
    }

    @Test
    @DisplayName("a blank token fails validation")
    void registerBlankToken() throws Exception {
        mvc.perform(post("/api/v1/devices").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": " "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.token").exists());

        verifyNoInteractions(deviceTokenService);
    }

    @Test
    @DisplayName("registering without a token is a 401")
    void registerUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token"}"""))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(deviceTokenService);
    }

    @Test
    @DisplayName("DELETE /devices unregisters the token from the body")
    void unregister() throws Exception {
        mvc.perform(delete("/api/v1/devices").with(authed())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token"}"""))
                .andExpect(status().isOk());

        verify(deviceTokenService).unregister("fcm-token");
    }
}
