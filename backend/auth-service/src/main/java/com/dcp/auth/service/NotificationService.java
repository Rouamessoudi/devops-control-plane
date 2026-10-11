package com.dcp.auth.service;

import com.dcp.auth.api.NotificationResponse;
import com.dcp.auth.domain.Notification;
import com.dcp.auth.repo.NotificationRepository;
import com.dcp.auth.repo.UserRepository;
import com.dcp.common.error.ApiException;
import com.dcp.common.web.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine() {
        UUID userId = CurrentUser.require().userId();
        return notifications.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markRead(UUID notificationId) {
        UUID userId = CurrentUser.require().userId();
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.setReadFlag(true);
        notifications.save(notification);
    }

    @Transactional
    public void create(UUID userId, String title, String message) {
        if (userId == null) {
            throw ApiException.badRequest("userId is required");
        }
        if (!users.existsById(userId)) {
            throw ApiException.notFound("User not found");
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(requireText(title, "title", 255));
        notification.setMessage(requireText(message, "message", 2000));
        notification.setReadFlag(false);
        notifications.save(notification);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw ApiException.badRequest(field + " is too long");
        }
        return trimmed;
    }
}
