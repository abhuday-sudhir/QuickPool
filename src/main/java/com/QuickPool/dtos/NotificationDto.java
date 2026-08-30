package com.QuickPool.dtos;

import com.QuickPool.entity.Notification;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class NotificationDto {
    private final UUID id;
    private final String title;
    private final String body;
    private final String type;
    private final UUID entityId;
    private final boolean read;
    private final LocalDateTime createdAt;

    public NotificationDto(Notification n) {
        this.id = n.getId();
        this.title = n.getTitle();
        this.body = n.getBody();
        this.type = n.getType().name();
        this.entityId = n.getEntityId();
        this.read = Boolean.TRUE.equals(n.getRead());
        this.createdAt = n.getCreatedAt();
    }
}
