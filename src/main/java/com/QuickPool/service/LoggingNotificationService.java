package com.QuickPool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoggingNotificationService implements NotificationService{
    @Override
    public void notifyUser(java.util.UUID userId, String title, String message) {
        // Placeholder — swap this body for real APNs/FCM calls later.
        // Keeping the interface the same means nothing else in the app changes.
        log.info("[NOTIFY] user={} title='{}' message='{}'", userId, title, message);
    }
}
