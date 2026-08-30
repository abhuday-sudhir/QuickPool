package com.QuickPool.service;

import com.QuickPool.entity.Notification;
import com.QuickPool.enums.NotificationType;
import com.QuickPool.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class PersistentNotificationService implements NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Override
    public void notifyUser(UUID userId, String title, String message, NotificationType type, UUID entityId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setBody(message);
        n.setType(type);
        n.setEntityId(entityId);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);

        log.info("[NOTIFY] user={} type={} title='{}'", userId, type, title);

        // FCM hook: once google-services.json and a service account exist, send the push
        // here using the same title/message/entityId. The inbox row above is the source of
        // truth either way, so a failed push never loses the notification.
    }
}
