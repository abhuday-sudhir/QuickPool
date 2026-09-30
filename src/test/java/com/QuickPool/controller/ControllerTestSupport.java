package com.QuickPool.controller;

import com.QuickPool.config.SecurityConfig;
import com.QuickPool.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Shared setup for the {@code @WebMvcTest} controller slices.
 *
 * Requests go through the real {@link SecurityConfig} and JwtAuthenticationFilter, so the
 * tests also pin down which routes need a token and that a missing one is a 401 (the app's
 * OkHttp refresh only reacts to 401). Only {@link JwtService} is mocked: {@link #authed()}
 * sends a bearer token it has been told is valid for {@link #userId}.
 */
@Import(SecurityConfig.class)
abstract class ControllerTestSupport {

    static final String TOKEN = "test-access-token";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtService jwtService;

    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubJwt() {
        when(jwtService.isValid(TOKEN)).thenReturn(true);
        when(jwtService.extractUserId(TOKEN)).thenReturn(userId);
    }

    static RequestPostProcessor authed() {
        return request -> {
            request.addHeader("Authorization", "Bearer " + TOKEN);
            return request;
        };
    }
}
