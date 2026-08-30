package com.QuickPool.service;

import com.QuickPool.enums.NotificationType;

import java.util.UUID;

public interface NotificationService {

    /**
     * Deliver a notification to a user. Currently persists it so the app can pull it
     * from the Alerts inbox; push delivery is added behind this same call.
     *
     * @param entityId the booking or ride offer the notification refers to, for deep-linking
     */
    void notifyUser(UUID userId, String title, String message, NotificationType type, UUID entityId);
}
