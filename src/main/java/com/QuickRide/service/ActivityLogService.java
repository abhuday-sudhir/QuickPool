package com.QuickRide.service;

import com.QuickRide.entity.ActivityLog;
import com.QuickRide.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    public void log(UUID userId, String action, String entityType, UUID entityId, String metadata) {
        ActivityLog entry = new ActivityLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setMetadata(metadata);
        entry.setCreatedAt(LocalDateTime.now());
        activityLogRepository.save(entry);
    }
}