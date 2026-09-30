package com.QuickPool.controller;

import com.QuickPool.entity.Notification;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends ControllerTestSupport {

    @MockitoBean private NotificationRepository notificationRepository;

    private Notification notification(UUID owner) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setUserId(owner);
        n.setTitle("Booking accepted");
        n.setBody("Your seat is confirmed");
        n.setType(NotificationType.BOOKING_ACCEPTED);
        n.setEntityId(UUID.randomUUID());
        n.setCreatedAt(LocalDateTime.of(2030, 1, 1, 8, 0));
        return n;
    }

    @Test
    @DisplayName("GET lists the caller's notifications, newest first, as a page envelope")
    void list() throws Exception {
        Notification n = notification(userId);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(n), PageRequest.of(0, 20), true));

        mvc.perform(get("/api/v1/notifications").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].id").value(n.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Booking accepted"))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_ACCEPTED"))
                .andExpect(jsonPath("$.content[0].entityId").value(n.getEntityId().toString()))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    @DisplayName("GET /unread-count wraps the count in an object")
    void unreadCount() throws Exception {
        when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(3L);

        mvc.perform(get("/api/v1/notifications/unread-count").with(authed()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("PUT /{id}/read marks the caller's own notification read")
    void markRead() throws Exception {
        Notification n = notification(userId);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        mvc.perform(put("/api/v1/notifications/{id}/read", n.getId()).with(authed()))
                .andExpect(status().isOk());

        assertThat(n.getRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("marking an unknown notification read is a 404")
    void markReadNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/notifications/{id}/read", id).with(authed()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    @DisplayName("marking someone else's notification read is a 403 and changes nothing")
    void markReadForbidden() throws Exception {
        Notification n = notification(UUID.randomUUID());
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        mvc.perform(put("/api/v1/notifications/{id}/read", n.getId()).with(authed()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not your notification"));

        assertThat(n.getRead()).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT /read-all marks everything read for the caller only")
    void markAllRead() throws Exception {
        mvc.perform(put("/api/v1/notifications/read-all").with(authed()))
                .andExpect(status().isOk());

        verify(notificationRepository).markAllRead(userId);
    }

    @Test
    @DisplayName("notifications need a token")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationRepository);
    }
}
