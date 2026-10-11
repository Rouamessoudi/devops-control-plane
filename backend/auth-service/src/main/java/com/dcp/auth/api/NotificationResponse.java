package com.dcp.auth.api;

import com.dcp.auth.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID userId, String title, String message, boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isReadFlag(),
                notification.getCreatedAt());
    }
}
