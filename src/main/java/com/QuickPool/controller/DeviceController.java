package com.QuickPool.controller;

import com.QuickPool.dtos.RegisterDeviceDto;
import com.QuickPool.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    @Autowired
    private DeviceTokenService deviceTokenService;

    @PostMapping
    public void register(@Valid @RequestBody RegisterDeviceDto body, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        deviceTokenService.register(userId, body.getToken(), body.getPlatform());
    }

    /**
     * Takes the token in the body rather than the path: an FCM token is long and contains
     * characters that do not survive a path segment cleanly.
     */
    @DeleteMapping
    public void unregister(@Valid @RequestBody RegisterDeviceDto body) {
        deviceTokenService.unregister(body.getToken());
    }
}
