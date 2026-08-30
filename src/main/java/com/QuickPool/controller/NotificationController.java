package com.QuickPool.controller;

import com.QuickPool.dtos.NotificationDto;
import com.QuickPool.entity.Notification;
import com.QuickPool.exception.ForbiddenException;
import com.QuickPool.exception.NotFoundException;
import com.QuickPool.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping
    public List<NotificationDto> list(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDto::new)
                .collect(Collectors.toList());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return Map.of("count", notificationRepository.countByUserIdAndReadFalse(userId));
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw new ForbiddenException("Not your notification");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @PutMapping("/read-all")
    @Transactional
    public void markAllRead(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        notificationRepository.markAllRead(userId);
    }
}
